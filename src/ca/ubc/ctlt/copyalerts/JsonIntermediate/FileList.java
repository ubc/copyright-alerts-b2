package ca.ubc.ctlt.copyalerts.JsonIntermediate;

import java.util.HashMap;
import java.util.List;

public class FileList
{
	HashMap<String, CourseFiles> courses;
	int page;
	int numPages;

	/**
	 * @param courses
	 * @param page
	 * @param numPages
	 */
	public FileList(HashMap<String, CourseFiles> courses, int page, int numPages)
	{
		super();
		this.courses = courses;
		this.page = page;
		this.numPages = numPages;
	}

	/**
	 * @return the courses
	 */
	public HashMap<String, CourseFiles> getCourses()
	{
		return courses;
	}
}
