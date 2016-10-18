package ca.ubc.ctlt.copyalerts.db.operations;

import blackboard.cms.filesystem.CSFile;
import blackboard.data.course.Course;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;
import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.StatusTable;
import ca.ubc.ctlt.copyalerts.db.entities.File;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;
import ca.ubc.ctlt.copyalerts.indexer.IndexGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FilesTableUpdateScanProcessor extends ScanProcessor
{
	private final static Logger logger = LoggerFactory.getLogger(FilesTableUpdateScanProcessor.class);

	private Set<Id> filesToRemove = new HashSet<>();
	private long rownum;
	private long file_pk1;
	private IndexGenerator indexGen;
	private FilesTable filestable;
	private StatusTable statustable;

	public FilesTableUpdateScanProcessor(IndexGenerator indexGen, StatusTable statustable) {
		this.indexGen = indexGen;
		this.statustable = statustable;
		this.filestable = new FilesTable();
	}


	@Override
	public void scan(Map<String, String> result) throws PersistenceException {
		String path = result.get("filepath");
		rownum = Long.parseLong(result.get("rownum"));
		file_pk1 = Long.parseLong(result.get("pk1"));
		Id pk1 = Id.generateId(File.DATA_TYPE, file_pk1);

		// check if the course is still available
		Course course = getCourseByPath(path);
		if (course == null || !course.getIsAvailable() ||
				(course.getEndDate() != null && course.getEndDate().before(Calendar.getInstance()))) {
			filesToRemove.add(pk1);
		} else {
			// check to see if this file has been removed or tagged since we last checked
			CSFile file = indexGen.getCSFileFromPath(path);

			if (null == file || indexGen.fileIsTagged(file)) {
				filesToRemove.add(pk1);
			}
		}

		// store the current batch into the queue when we've got enough
		if (filesToRemove.size() >= CSIndexJob.BATCHSIZE) {
			save();
		}
	}

	@Override
	public void cleanup(boolean wasInterrupted)
	{
		if (!filesToRemove.isEmpty()) {
			// make sure the last incomplete batch isn't missed
			save();
		}
		if (!wasInterrupted) {
			// make sure to reset queue resume data if we've gone a full run without problems
			statustable.saveFileResumeData(0, 0);
			logger.debug("Finished processing. Reset Resume Data - Offset: 0 (was " + rownum + ") File ID: 0 (was " + file_pk1 + ")");
		}
	}

	/**
	 * delete all processed files from file table and save the status in case we need a resume in next run
	 */
	private void save() {
		filestable.deleteFilesByPk1(filesToRemove);
		logger.debug("Removed " + filesToRemove.size() + " from file table");
		filesToRemove.clear(); // empty the current batch now that they're safely stored

		// save resume data since we weren't finished
		logger.debug("Saving Resume Data - Offset: " + rownum + " File ID: " + file_pk1);
		statustable.saveFileResumeData(rownum, file_pk1);
	}

	public static Course getCourseByPath(String path) {
		// path has to be a course path
		if (!path.startsWith("/courses/")) {
			return null;
		}

		String courseStr = path.split("/")[2];

		Course course = null;

		try {
			course = CourseDbLoader.Default.getInstance().loadByCourseId(courseStr);
		} catch (PersistenceException e) {
			logger.info("Could not find course " + courseStr);
		}

		return course;
	}
}
