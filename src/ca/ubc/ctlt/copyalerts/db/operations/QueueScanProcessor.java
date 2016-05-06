package ca.ubc.ctlt.copyalerts.db.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.db.StatusTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.db.QueueTable;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;

public class QueueScanProcessor extends ScanProcessor
{
	private final static Logger logger = LoggerFactory.getLogger(QueueScanProcessor.class);

	/**
	 * Single regex to check the filepath to make sure that we're only picking up
	 * course files the general format for course files is: /courses/coursename/file
	 */
	private Pattern courseFilePattern = Pattern.compile("^/courses/[^/]+?/.+$");
	private Matcher courseFileMatcher = courseFilePattern.matcher("");
	/**
	 * Stores a batch of file paths to be put into the queue
	 */
	private List<String> paths = new ArrayList<>();
	/**
	 * Database access
	 */
	private QueueTable queuetable = new QueueTable();
	private StatusTable statusTable;
	/**
	 * Keeps track of how many files we've processed for this batch
	 */
	private int batchCount = 0;
	/**
	 * Keeps track of resume data for this scan.
	 */
	private long rownum = 0;
	private long file_id = 0;


	public QueueScanProcessor(StatusTable statusTable)
	{
		this.statusTable = statusTable;
	}

	/**
	 * - rownum: 1
	 * - path: /courses/coursename/file
	 * - fileid: 12
	 * @throws InaccessibleDbException 
	 */
	@Override
	public void scan(Map<String, String> result) throws InaccessibleDbException
	{
		String path = result.get("full_path");
		rownum = Long.parseLong(result.get("rownum"));
		file_id = Long.parseLong(result.get("file_id"));
		// make sure that we only have course files and no xid- files
		// it seems that xid- files are not listed in the xyf_urls table, 
		// but better be safe with an explicit check
		if (courseFileMatcher.reset(path).matches() &&
			!path.contains("xid-"))
		{
			paths.add(path);
		}
		// store the current batch into the queue when we've got enough
		if (paths.size() >= CSIndexJob.BATCHSIZE)
		{
			logger.debug("Added to queue: " + path);
			queuetable.add(paths);
			paths.clear(); // empty the current batch now that they're safely stored
		}
		if (batchCount >= CSIndexJob.BATCHSIZE) 
		{
			statusTable.saveQueueResumeData(rownum, file_id);
			batchCount = 0;
		}
		batchCount++;
		
	}

	@Override
	public void cleanup(boolean wasInterrupted) throws InaccessibleDbException
	{
		if (!paths.isEmpty())
		{ // make sure the last incomplete batch isn't missed
			queuetable.add(paths);
			paths.clear(); // empty the current batch now that they're safely stored
		}
		if (wasInterrupted)
		{ // save resume data since we weren't finished
			logger.debug("Saving Resume Data - Offset: " + rownum + " File ID: " + file_id);
			statusTable.saveQueueResumeData(rownum, file_id);
		}
		else
		{ // make sure to reset queue resume data if we've gone a full run without problems
			logger.debug("Reset Resume Data - Offset: " + rownum + " File ID: " + file_id);
			statusTable.saveQueueResumeData(0, 0);
		}
		
	}
	

}
