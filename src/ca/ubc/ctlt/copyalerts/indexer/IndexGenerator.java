package ca.ubc.ctlt.copyalerts.indexer;

import java.util.ArrayList;
import java.util.List;

import blackboard.cms.filesystem.CSAccessControlEntry;
import blackboard.cms.filesystem.CSEntryMetadata;
import blackboard.cms.filesystem.CSFile;
import blackboard.data.course.Course;
import blackboard.data.course.CourseMembership;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;
import blackboard.persist.course.CourseMembershipDbLoader;

public class IndexGenerator
{
	// class to retrieve file ownership information and prep it for output into sql database
	private ArrayList<String> attributes;
	
	public IndexGenerator(ArrayList<String> attributes)
	{
		this.attributes = attributes;
	}
	
	public void process(CSFile file)
	{
		CSEntryMetadata meta = file.getCSEntryMetadata();
		System.out.println("Attributes: " + attributes.size());
		for (String attr : attributes)
		{
			String res = meta.getStandardProperty(attr);
			if (!res.isEmpty())
			{ // there's a value for this attribute ID, so we can assume it's been copyright tagged
				// there might be a concern if we don't check whether a user has actually selected a copyright
				// though the metadata building block UI prevents that scenario so it should be ok?
				return;
			}
		}
		// the file has not been copyright tagged, store it in the database
		try 
		{
			// We only want ISS and Instructor users for now
			// can ignore organizations for now
			// only worry about course instructors and iss
			System.out.println("Blah");
			CSAccessControlEntry[] accesses = file.getAccessControlEntries();
			System.out.println("Accesses: " + accesses.length);
			for (CSAccessControlEntry e : accesses)
			{
				if (!e.canWrite())
				{ // skip if can't write
					continue;
				}
				String pid = e.getPrincipalID();
				if (pid.startsWith("G:CR"))
				{ // this entry describes a course role
					if (pid.contains("(?i)instructor"))
					{
						ArrayList<String> names = getInstructors(pid);
					}
				}
				String test = "";
				if (e.canWrite())
				{
					test = " write";
				}
				System.out.println(e.getPrincipalID() + test);
			}
		} catch (Exception e)
		{
			System.out.println("Died: ");
			e.printStackTrace();
		}
	}
	
	/**
	 * Get a list of usernames who are instructors in the given course.
	 * @param pid - the principal id returned from CSAccessControlEntry, should be in a form like G:CR:CL.UBC.MATH.101.201.2012W2.13204:INSTRUCTOR
	 * so will have to parse the course name out from it.
	 * @return
	 * @throws PersistenceException 
	 */
	private ArrayList<String> getInstructors(String pid) throws PersistenceException
	{
		ArrayList<String> names = new ArrayList<String>();
		String[] pidArr = pid.split(":");
		String courseName = pidArr[2];
		try
		{
			Course course = CourseDbLoader.Default.getInstance().loadByBatchUid(courseName);
			List<CourseMembership> memberships = 
					CourseMembershipDbLoader.Default.getInstance().loadByCourseIdAndRole(course.getId(), CourseMembership.Role.INSTRUCTOR);
			// NOTE: CourseMembership.Role.Instructor might not work on Connect as we have custom role names
			for (CourseMembership membership : memberships)
			{
				membership.getUser().getUserName();
				System.out.println("Instructor username: " + membership.getUser().getUserName());
			}
		} catch (KeyNotFoundException e)
		{ // Course not found, skip
			System.out.println("Course not found?");
			e.printStackTrace();
			return names;
		} 
		
		return names;
	}

}
