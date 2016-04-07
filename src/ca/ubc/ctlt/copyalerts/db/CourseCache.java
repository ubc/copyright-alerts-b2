package ca.ubc.ctlt.copyalerts.db;

import java.util.HashMap;

import blackboard.data.course.Course;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.course.CourseDbLoader;

public class CourseCache
{
	private class CourseInfo
	{
		public String courseId;
		public String courseTitle;
	}

	private HashMap<String, CourseInfo> courses = new HashMap<String, CourseInfo>();

	public boolean addIfValidCourse(String courseName) throws PersistenceException
	{
		if (courses.containsKey(courseName))
		{
			return true;
		}

		try
		{
			CourseDbLoader cload = CourseDbLoader.Default.getInstance();
			Course course = cload.loadByCourseId(courseName);
			CourseInfo info = new CourseInfo();
			info.courseId = course.getId().toExternalString();
			info.courseTitle = course.getTitle();
			courses.put(courseName, info);
			return true;
		} catch(KeyNotFoundException e)
		{
			return false;
		}
	}

	public String getCourseTitle(String courseName) throws PersistenceException
	{
		if (!courses.containsKey(courseName))
		{
			if(!addIfValidCourse(courseName)) {
				return null;
			}
		}
		return courses.get(courseName).courseTitle;
	}

	public String getCourseId(String courseName) throws PersistenceException
	{
		if (!courses.containsKey(courseName))
		{
			if(!addIfValidCourse(courseName)) {
				return null;
			}
		}
		return courses.get(courseName).courseId;
	}

}
