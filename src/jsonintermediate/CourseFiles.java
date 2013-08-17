package jsonintermediate;

import java.util.ArrayList;

public class CourseFiles
{
	public String name;
	public int numFiles;
	public ArrayList<FilePath> files = new ArrayList<FilePath>();
	
	/**
	 * Assuming that all entries in paths belongs in the same course.
	 * 
	 * @param paths
	 */
	public CourseFiles(String course, ArrayList<String> paths)
	{
		if (paths.isEmpty())
		{
			return;
		}
		
		this.name = course;
		numFiles = paths.size();
		
		for (String p : paths)
		{
			files.add(new FilePath(p));
		}
	}
	
}
