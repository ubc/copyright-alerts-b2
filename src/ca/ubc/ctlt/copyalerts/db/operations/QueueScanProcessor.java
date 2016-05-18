package ca.ubc.ctlt.copyalerts.db.operations;

import ca.ubc.ctlt.copyalerts.db.QueueTable;
import ca.ubc.ctlt.copyalerts.db.StatusTable;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	 */
	@Override
	public void scan(Map<String, String> result) {
		String path = result.get("full_path");
		rownum = Long.parseLong(result.get("rownum"));
		file_id = Long.parseLong(result.get("file_id"));
		// make sure that we only have course files and no xid- files
		// it seems that xid- files are not listed in the xyf_urls table,
		// but better be safe with an explicit check
		if (courseFileMatcher.reset(path).matches() && !path.contains("xid-")) {
			paths.add(path);
		} else {
			logger.debug("Skipping " + path + " as it doesn't match patter " +
					courseFilePattern + " or it contains xid-");
		}
		// store the current batch into the queue when we've got enough
		if (paths.size() >= CSIndexJob.BATCHSIZE) {
			save();
		}
	}

	@Override
	public void cleanup(boolean wasInterrupted) {
		if (!paths.isEmpty()) {
			// make sure the last incomplete batch isn't missed
			save();
		}
		if (!wasInterrupted) {
			// make sure to reset queue resume data if we've gone a full run without problems
			statusTable.saveQueueResumeData(0, 0);
			logger.debug("Finished processing. Reset Resume Data - Offset: 0 (was " + rownum + ") File ID: 0 (was " + file_id + ")");
		}
	}

	/**
	 * Save all processed paths to queue table and save the status in case we need a resume in next run
	 */
	private void save() {
		queuetable.add(paths);
		logger.debug("Added " + paths.size() + " paths to queue." );
		// empty the current batch now that they're safely stored
		paths.clear();

		// save resume data since we weren't finished
		logger.debug("Saving Resume Data - Offset: " + rownum + " File ID: " + file_id);
		statusTable.saveQueueResumeData(rownum, file_id);
	}

}
