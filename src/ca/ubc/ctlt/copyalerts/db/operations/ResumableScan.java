package ca.ubc.ctlt.copyalerts.db.operations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.db.DbInit;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;

public class ResumableScan implements Runnable
{
	private final static Logger logger = LoggerFactory.getLogger(ResumableScan.class);

	private ScanProcessor processor;
	private ResumableScanInfo info;
	private Boolean errorLock = true;
	private Exception error = null;

	public ResumableScan(ResumableScanInfo info, ScanProcessor processor)
	{
		super();
		this.info = info;
		this.processor = processor;
	}

	/**
	 * Generate the list of files to check for indexing.
	 * Using the API to iterate through the content system turns out to be too memory consuming
	 * so let's try reading the database directly.
	 * @return
	 * @throws JobExecutionException 
	 */
	public void run()
	{
		ConnectionManager cm = DbInit.getConnectionManager();
		Connection conn = null;

		try
		{
			conn = cm.getConnection();
			conn.setAutoCommit(false); // need to disable autocommit to enable
										// fetching only a small number of rows at once necessary to keep memory usage low
			
			long lastQueueFileid = info.getRowIdVal();
			long queueOffset = 0; 
			if (lastQueueFileid != 0)
			{ // queue generation was interrupted, let's try to resume
				// find out where the last file id we saw is at
				// Example Query: SELECT * FROM (SELECT rownum, file_id FROM bblearn_cms_doc.xyf_urls ORDER BY file_id) WHERE file_id=123
				String query =  "SELECT * FROM (SELECT rownum, "+ info.getRowIdKey() +
								" FROM "+ info.getTableName() +" ORDER BY "+ info.getRowIdKey() +
								") WHERE "+ info.getRowIdKey() +"=?";
				PreparedStatement stmt = conn.prepareStatement(query);
				stmt.setLong(1, lastQueueFileid);

				long startTime = System.currentTimeMillis();
				ResultSet res = stmt.executeQuery();
				if (res.next())
				{ // good, file wasn't deleted, set our offset to it
					logger.debug("Resuming from offset of last processed file.");
					queueOffset = res.getLong(1);
				}
				else
				{ // file was deleted! have to use the fall back option
					// just using the stored offset means that we might miss some files, but hopefully this isn't a common occurrence 
					logger.debug("Can't find last processed file, blindly resuming from last offset.");
					queueOffset = info.getRowOffset();
				}
				long endTime = System.currentTimeMillis();
				long interval = endTime - startTime;
				logger.debug("Resume Scan Query Time: " + interval / 1000.0 + " seconds");
				res.close();
			}
			
			
			String dataColumnNames = "";
			for (String name : info.getDataColumnNames())
			{
				dataColumnNames += name + ", ";
			}
			String query = "";
			// complex query for resume, simpler for generating a new queue
			if (queueOffset > 0)
			{ // resuming from an interrupted queue generation
				// Example query: SELECT rn, full_path, file_id FROM 
				// (SELECT full_path, file_id, ROW_NUMBER() OVER (ORDER BY file_id) rn FROM BBLEARN_CMS_DOC.xyf_urls) 
				// WHERE rn > 123
				query = "SELECT rn, "+ dataColumnNames + info.getRowIdKey() + " FROM " +
						"(SELECT "+ dataColumnNames + info.getRowIdKey() +", ROW_NUMBER() OVER " +
						"(ORDER BY "+ info.getRowIdKey() +") rn FROM "+ info.getTableName() +") " +
						"WHERE rn > " + queueOffset;
			}
			else
			{ // starting a new queue generation
				query = "SELECT rownum, "+ dataColumnNames + info.getRowIdKey() + " FROM "+
					info.getTableName() +" ORDER BY " + info.getRowIdKey();
			}
			PreparedStatement queryCompiled = conn.prepareStatement(query);
			queryCompiled.setFetchSize(CSIndexJob.BATCHSIZE); // limit the number of rows we're pre-fetching

			int count = 0;

			ResultSet res = queryCompiled.executeQuery();
			boolean isInterrupted = false;
			// iterate through the results
			while (res.next())
			{
				// call the processor on each result
				Map<String, String> result = new HashMap<String, String>();
				// add the mandatory rownum to result
				result.put("rownum", res.getString(1));
				// add the mandatory row id  to result
				result.put(info.getRowIdKey(), res.getString(info.getRowIdKey()));
				// add the columns that the user specified they want
				for (String name: info.getDataColumnNames())
				{
					result.put(name, res.getString(name));
				}
				processor.scan(result);
				
				// Note: The interrupted flag is cleared by calling interrupted(), so must store it
				isInterrupted = Thread.interrupted();
				if (count >= CSIndexJob.BATCHSIZE && isInterrupted)
				{
					logger.debug("ResumableScan Interrupted.");
					count = 0;
					break;
				}
				count++;
			}
			// make sure the last batch of files are added
			processor.cleanup(isInterrupted);
			res.close();
			queryCompiled.close();
			conn.commit(); // we're running only selects, but just in case
		} catch (SQLException e)
		{
			logger.error(e.getMessage(), e);
			setError(e);
			return;
		} catch (ConnectionNotAvailableException e)
		{
			logger.error(e.getMessage(), e);
			setError(e);
			return;
		} catch (InaccessibleDbException e)
		{
			logger.error("Could not access database, stopping index job.", e);
			setError(e);
			return;
		} finally
		{
			if (conn != null)
				cm.releaseConnection(conn);
		}
	}
	
	public boolean hasError()
	{
		synchronized (errorLock)
		{
			return error != null;
		}
	}
	
	public void setError(Exception e)
	{
		synchronized (errorLock)
		{
			error = e;
		}
	}
	
	public Exception getError()
	{
		synchronized (errorLock)
		{
			return error;
		}
	}
}
