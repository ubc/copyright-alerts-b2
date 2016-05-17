package ca.ubc.ctlt.copyalerts.db;

import blackboard.persist.KeyNotFoundException;
import blackboard.persist.dao.impl.SimpleDAO;
import blackboard.persist.impl.SimpleCountQuery;
import blackboard.persist.impl.SimpleSelectQuery;
import blackboard.persist.impl.mapping.DbObjectMap;
import blackboard.persist.impl.mapping.annotation.AnnotationMappingFactory;
import ca.ubc.ctlt.copyalerts.db.entities.QueueItem;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class QueueTable extends SimpleDAO<QueueItem>
{
	private static final DbObjectMap QUEUE_EXT_MAP = AnnotationMappingFactory.getMap(QueueItem.class);

	private final static Logger logger = LoggerFactory.getLogger(QueueTable.class);

	public QueueTable()
	{
		super(QUEUE_EXT_MAP);
	}

	/**
	 * Add an array of paths of files to be processed to the queue.
	 * @param paths array of paths
	 */
	public void add(List<String> paths)
	{
		InsertBulkQuery query = new InsertBulkQuery(this.getDAOSupport().getMap());
		for (String path : paths) {
			QueueItem queueItem = new QueueItem(path);
			query.addObject(queueItem);
		}
		if (query.getObjectsToInsert().size() > 0) {
			getDAOSupport().execute(query);
		} else {
			// nothing to insert, close query instead
			query.close();
		}
//		// Not sure if cm can be made into a private field, but it looks like connections have to be released or Oracle
//		// will not be happy
//		Connection conn = null;
//		String query = "insert into "+ TABLENAME +" (pk1, filepath) values ("+ TABLENAME +"_seq.nextval, ?)";
//		PreparedStatement stmt;
//		try
//		{
//			conn = cm.getConnection();
//			// convert the query string into a compiled statement for faster execution
//			stmt = conn.prepareStatement(query);
//
//			for (String path : paths)
//			{
//				stmt.setString(1, path);
//				stmt.executeUpdate();
//			}
//			stmt.close();
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		}
//		finally
//		{
//			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
//		}
	}

	public List<QueueItem> load()
	{
		return load(CSIndexJob.BATCHSIZE);
	}

	/**
	 * Get the next set of files paths to be processed.
	 * Load num rows from the queue. Remember to call pop() to read the next batch off the queue.
	 *
	 * @param num number of QueueItems to load
	 * @return List of QueueItems
	 */
	public List<QueueItem> load(int num)
	{
		SimpleSelectQuery query = new SimpleSelectQuery(this.getDAOSupport().getMap());
		query.addOrderBy("id", true);
		query.setMaxRows(num);

		return getDAOSupport().loadList(query);

//		Connection conn = null;
//
//		ArrayList<String> ret = new ArrayList<String>();
//		try
//		{
//			conn = cm.getConnection();
//			// note that there's no order guarantee from just select rownum statements, so have to use the order by subquery
//			// to impose a repeatable order on the return results
//			String query = "SELECT filepath FROM (SELECT * FROM "+ TABLENAME +" ORDER BY pk1) WHERE rownum <= " + num;
//	        PreparedStatement queryCompiled = conn.prepareStatement(query);
//	        ResultSet res = queryCompiled.executeQuery();
//
//	        while(res.next())
//	        {
//	        	ret.add(res.getString(1)); // store the path for return
//	        }
//
//	        res.close();
//
//	        queryCompiled.close();
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		}
//		finally
//		{
//			if (conn != null) cm.releaseConnection(conn);
//		}
//
//        return ret;
	}

	public void pop()
	{
		pop(CSIndexJob.BATCHSIZE);
	}

	/**
	 * Remove num rows from the queue table.
	 * @param num number of QueueItems to pop
	 */
	public void pop(int num)
	{
		DeleteTopQuery query = new DeleteTopQuery(getDAOSupport().getMap(), num);
		this.getDAOSupport().delete(query);
//		pop(load(num));
//		Connection conn = null;
//		try
//		{
//			conn = cm.getConnection();
//			String query = "DELETE FROM "+ TABLENAME +" WHERE pk1 IN (SELECT pk1 FROM (SELECT * FROM "+ TABLENAME +" ORDER BY pk1) WHERE rownum <= "+ num +")";
//			PreparedStatement deleteQuery = conn.prepareStatement(query); // don't need prepared statement for a single query, but can't be bothered to change
//			deleteQuery.executeUpdate();
//			deleteQuery.close();
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		}
//		finally
//		{
//			if (conn != null) cm.releaseConnection(conn);
//		}
	}

	// Get the number of entries in this table
	public long getCount()
	{
		long ret = 0;

		SimpleCountQuery query = new SimpleCountQuery(this.getDAOSupport().getMap(), "count");
		try {
			ret = getDAOSupport().loadResult(query, Integer.class);
		} catch (KeyNotFoundException e) {
			e.printStackTrace();
		}

        return ret;
	}

}
