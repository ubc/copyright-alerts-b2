package ca.ubc.ctlt.copyalerts.indexer;

import java.util.ArrayList;
import java.util.List;

import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import blackboard.cms.filesystem.CSAccessControlEntry;
import blackboard.cms.filesystem.CSEntryMetadata;
import blackboard.cms.filesystem.CSFile;
import blackboard.data.course.Course;
import blackboard.data.course.CourseMembership;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;
import blackboard.persist.course.CourseMembershipDbLoader;

public class IndexGenerator
{
	// class to retrieve file ownership information and prep it for output into sql database
	private ArrayList<String> attributes;
	private FilesTable ft = new FilesTable();
	
	public IndexGenerator(ArrayList<String> attributes)
	{
		this.attributes = attributes;
	}
	
	public void process(CSFile file) throws PersistenceException, InaccessibleDbException
	{
		if (fileIsTagged(file))
		{
			return;
		}
		// the file has not been copyright tagged, store it in the database
		CSAccessControlEntry[] accesses = file.getAccessControlEntries();
		ArrayList<Id> names = new ArrayList<Id>();
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
				// We only want Instructor users for now
				if (pid.matches(".+(?i)instructor.*"))
				{
					names.addAll(getInstructors(course.getId()));
				}
			}
		}
		ft.add(file, names);
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
	 * Get a list of userids who are instructors in the given course.
	 * @return
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
	 * @param courseId
	 * @param role
	 * @return
	 * @throws PersistenceException
	 */
	private ArrayList<Id> getUserWithRoleInCourse(Id courseId, CourseMembership.Role role) throws PersistenceException
	{
		ArrayList<Id> names = new ArrayList<Id>();
		List<CourseMembership> memberships = CourseMembershipDbLoader.Default.getInstance().loadByCourseIdAndRole(courseId, role);
		for (CourseMembership membership : memberships)
		{
			names.add(membership.getUserId());
		}
		return names;
	}
	
}