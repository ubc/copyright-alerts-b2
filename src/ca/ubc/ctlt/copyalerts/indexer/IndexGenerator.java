package ca.ubc.ctlt.copyalerts.indexer;

import blackboard.cms.filesystem.*;
import blackboard.data.course.Course;
import blackboard.data.course.CourseMembership;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;
import blackboard.persist.course.CourseMembershipDbLoader;
import ca.ubc.ctlt.copyalerts.db.FilesTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class IndexGenerator
{
	private final static Logger logger = LoggerFactory.getLogger(IndexGenerator.class);
	// class to retrieve file ownership information and prep it for output into sql database
	private ArrayList<String> attributes;
	private FilesTable ft = new FilesTable();
	private CSContext ctx = CSContext.getContext();

	public IndexGenerator(ArrayList<String> attributes)
	{
		this.attributes = attributes;
		ctx.isSuperUser(true);
	}

	/**
	 * Process the files to file table with additional file info
	 * @param files list of files
	 * @return number of files are actually added to the table
	 * @throws PersistenceException
     */
	int process(List<CSFile> files) throws PersistenceException
	{
		Map<CSFile, Set<Id>> filesAndUsers = new HashMap<>();
		for (CSFile file : files)
		{
			if (fileIsTagged(file))
			{
				continue;
			}
			// the file has not been copyright tagged, store it in the database
			CSAccessControlEntry[] accesses = file.getAccessControlEntries();
			// Use a set to ensure that we end up with no duplicate users or a user might
			// end up with multiple entries for a single file. This might happen if a file
			// is shared by two or more courses taught by the same instructor, and so we
			// grab the instructors for both courses, ending up with the same instructor
			// twice.
			HashSet<Id> names = new HashSet<>();
			for (CSAccessControlEntry e : accesses)
			{
				if (!e.canWrite())
				{ // skip if can't write
					continue;
				}
				String pid = e.getPrincipalID();
				// Some sample principal IDs:
				// This is for course instructors, I'm assuming that CR stands for Course Role
				// G:CR:CL.UBC.MATH.101.201.2012W2.13204:INSTRUCTOR
				// This is for system admins, I'm assuming that SR stands for System Role
				// G:SR:SYSTEM_ADMIN
				// This is for a single user assigned permission to the file
				// BB:U:_81_1

				// Since we only want Instructors and ISS role
				if (pid.startsWith("G:CR"))
				{
					String[] pidArr = pid.split(":");
					String courseName = pidArr[2];
					Course course = CourseDbLoader.Default.getInstance().loadByCourseId(courseName);
					// we only add files that are in active course and the course is not ended yet
					Calendar endDate = course.getEndDate();
					if ((endDate != null && endDate.before(Calendar.getInstance())) || !course.getIsAvailable()) {
						continue;
					}
					// We only want Instructor users for now
					if (pid.matches(".+(?i)instructor.*"))
					{
						names.addAll(getInstructors(course.getId()));
					}
				}
			}
			if (!names.isEmpty()) {
				filesAndUsers.put(file, names);
			}
		}
		ft.add(filesAndUsers);

		return filesAndUsers.size();
	}

	public boolean fileIsTagged(CSFile file)
	{
		CSEntryMetadata meta = file.getCSEntryMetadata();
		for (String attr : attributes)
		{
			String res = meta.getStandardProperty(attr);
			if (!res.isEmpty())
			{ // there's a value for this attribute ID, so we can assume it's been copyright tagged
				// there might be a concern if we don't check whether a user has actually selected a copyright
				// though the metadata building block UI prevents that scenario so it should be ok?
				return true;
			}
		}
		return false;
	}

	/**
	 * Given a path, return the CSFile entry if the path is a valid file.
	 *
	 * @param path file path
	 * @return CSFile if path is a valid file, null otherwise.
	 */
	public CSFile getCSFileFromPath(String path)
	{
		CSEntry entry = ctx.findEntry(path);
		if (entry == null)
		{
			logger.info("Non-existent file: " + path);
			return null;
		}
		if (!(entry instanceof CSFile))
		{
			logger.info("Skipping directory, Path: " + path);
			return null;
		}
		return (CSFile) entry;
	}

	/**
	 * Get a list of userids who are instructors in the given course.
	 * @return list of user ids
	 * @throws PersistenceException
	 */
	private ArrayList<Id> getInstructors(Id courseId) throws PersistenceException
	{
		// TODO allow configurable role IDs
		CourseMembership.Role role = CourseMembership.Role.fromIdentifier("UBC_Instructor");
		if (role == null)
		{ // for testing on local dev instance
			role = CourseMembership.Role.INSTRUCTOR;
		}
		return getUserWithRoleInCourse(courseId, role);
	}

	/**
	 * Get all user in the given course with the given role. Return as an array of Ids
	 * @param courseId course id
	 * @param role course membership role
	 * @return list of user ids
	 * @throws PersistenceException
	 */
	private ArrayList<Id> getUserWithRoleInCourse(Id courseId, CourseMembership.Role role) throws PersistenceException
	{
		ArrayList<Id> names = new ArrayList<>();
		List<CourseMembership> memberships = CourseMembershipDbLoader.Default.getInstance().loadByCourseIdAndRole(courseId, role);
		for (CourseMembership membership : memberships)
		{
			names.add(membership.getUserId());
		}
		return names;
	}

}
