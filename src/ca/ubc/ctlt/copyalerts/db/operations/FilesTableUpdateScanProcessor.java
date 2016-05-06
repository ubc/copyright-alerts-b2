package ca.ubc.ctlt.copyalerts.db.operations;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import blackboard.cms.filesystem.CSFile;

import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.StatusTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;
import ca.ubc.ctlt.copyalerts.indexer.IndexGenerator;

public class FilesTableUpdateScanProcessor extends ScanProcessor
{
	private final static Logger logger = LoggerFactory.getLogger(FilesTableUpdateScanProcessor.class);
	
	private Set<Long> filesToRemove = new HashSet<>();
	private long rownum;
	private long file_pk1;
	private int batchCount;
	private IndexGenerator indexGen;
	private FilesTable filestable;
	private StatusTable statustable;
	
	public FilesTableUpdateScanProcessor(IndexGenerator indexGen, StatusTable statustable)
	{
		this.indexGen = indexGen;
		this.statustable = statustable;
		this.filestable = new FilesTable();
	}
	

	@Override
	public void scan(Map<String, String> result) throws InaccessibleDbException
	{
		String path = result.get("filepath");
		rownum = Long.parseLong(result.get("rownum"));
		file_pk1 = Long.parseLong(result.get("pk1"));
		
		// check to see if this file has been tagged since we last checked
		CSFile file = indexGen.getCSFileFromPath(path);
		
		if (path.equals("/courses/AAAA000/inceptiontest/0000/0990.txt"))
		{
			logger.debug("Is Tagged? " + indexGen.fileIsTagged(file)); // TODO REMOVE
		}
		
		if (indexGen.fileIsTagged(file))
		{
			filesToRemove.add(file_pk1);
		}
		
		// store the current batch into the queue when we've got enough
		if (filesToRemove.size() >= CSIndexJob.BATCHSIZE)
		{
			logger.debug("Removing Batch: " + path);
			filestable.deleteFilesByPk1(filesToRemove);
			filesToRemove.clear(); // empty the current batch now that they're safely stored
		}
		if (batchCount >= CSIndexJob.BATCHSIZE) 
		{
			statustable.saveFileResumeData(rownum, file_pk1);
			batchCount = 0;
		}
		batchCount++;
	}

	@Override
	public void cleanup(boolean wasInterrupted) throws InaccessibleDbException
	{
		logger.debug("File Update Cleanup Size: " + filesToRemove.size());
		if (!filesToRemove.isEmpty())
		{ // make sure the last incomplete batch isn't missed
			filestable.deleteFilesByPk1(filesToRemove);
			filesToRemove.clear(); // empty the current batch now that they're safely stored
		}
		if (wasInterrupted)
		{ // save resume data since we weren't finished
			logger.debug("Saving Resume Data - Offset: " + rownum + " File ID: " + file_pk1);
			statustable.saveFileResumeData(rownum, file_pk1);
		}
		else
		{ // make sure to reset queue resume data if we've gone a full run without problems
			logger.debug("Reset Resume Data - Offset: " + rownum + " File ID: " + file_pk1);
			statustable.saveFileResumeData(0, 0);
		}
	}

}
