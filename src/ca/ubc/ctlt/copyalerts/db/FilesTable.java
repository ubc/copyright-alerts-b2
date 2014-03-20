package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.CourseFiles;
import ca.ubc.ctlt.copyalerts.JsonIntermediate.FileList;
import ca.ubc.ctlt.copyalerts.JsonIntermediate.FilePath;

import blackboard.cms.filesystem.CSFile;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;

public class FilesTable
{
	private final static Logger logger = LoggerFactory.getLogger(FilesTable.class);

	private final static String TABLENAME = "ubc_ctlt_ca_files";
	private final static int ENTRYPERPAGE = 25;
	
	// store mapping of course names to course title, names are id like, title is human readable
	private CourseCache courses = new CourseCache();
	private ConnectionManager cm = DbInit.getConnectionManager();
	
	// have to load paging specially as each course can be paged
	// have a loadCourseFiles or getCourseFiles
	public FileList load(String userid) throws InaccessibleDbException
	{
		HashMap<String, CourseFiles> ret = loadAll(userid);
		for (Entry<String, CourseFiles> e : ret.entrySet())
		{
			CourseFiles cf = e.getValue();
			cf = splitToPage(cf, 1);
			ret.put(e.getKey(), cf);
		}
		
		return new FileList(ret, 1, 1);
	}
	
	public CourseFiles loadCourseFiles(String userid, String courseId, int page) throws InaccessibleDbException
	{
		HashMap<String, CourseFiles> list = loadAll(userid);
		if (list.containsKey(courseId))
		{
			CourseFiles cf = list.get(courseId);
			return splitToPage(cf, page);
		}
		return null;
	}

	public void add(CSFile file, Set<Id> users) throws InaccessibleDbException
	{
		// first, make sure that we're using a valid course
		String courseName = parseCourseName(file.getFullPath());
		if (courseName.isEmpty()) return; // failed to parse course name, so must be invalid
		boolean validCourse;
		try
		{
			validCourse = courses.addIfValidCourse(courseName);
			if (!validCourse) return; // couldn't retrieve course from cache or database, so must be invalid
		} catch (PersistenceException e)
		{
			throw new InaccessibleDbException("Unable to read from db", e);
		}
		
		// write to database
		Connection conn = null;
		String query = "insert into "+ TABLENAME +" (pk1, userid, course, filepath) values ("+ TABLENAME +"_seq.nextval, ?, ?, ?)";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			
			for (Id e : users)
			{
				stmt.setString(1, e.toExternalString());
				stmt.setString(2, courses.getCourseTitle(courseName));
				stmt.setString(3, file.getFullPath());
				stmt.executeUpdate();
			}
			stmt.close();
		} catch (SQLException e)
		{
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		} catch (PersistenceException e)
		{
			throw new InaccessibleDbException("Unable to read from db", e);
		} 
		finally
		{
			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
		}
	}
	
	public void deleteAll() throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "delete from "+ TABLENAME;
		Statement stmt;
		try
		{
			conn = cm.getConnection();
			stmt = conn.createStatement();
			stmt.executeUpdate(query);
		} catch (SQLException e)
		{
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
		}
	}
	
	public void deleteFile(String path) throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "delete from "+ TABLENAME + " where filepath = ?";
		try
		{
			conn = cm.getConnection();
			PreparedStatement stmt = conn.prepareStatement(query);
			stmt.setString(1, path);
			stmt.executeUpdate();
		} catch (SQLException e)
		{
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
		}
	}
	
	// Get the number of entries in this table
	public int getCount() throws InaccessibleDbException
	{
		Connection conn = null;
		int ret = 0;

		try 
		{
			conn = cm.getConnection();
			String query = "SELECT COUNT(*) FROM " + TABLENAME;
	        PreparedStatement queryCompiled = conn.prepareStatement(query);
	        ResultSet res = queryCompiled.executeQuery();

	        res.next();
        	ret = res.getInt(1);

	        res.close();
	        queryCompiled.close();
		} catch (SQLException e)
		{
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn);
		}

        return ret;
	}
	
	
	/**
	 * Parse out the course name from the full content system path.
	 * @param path
	 * @return
	 */
	private String parseCourseName(String path)
	{
		// try to parse out the course name from the path
		// first remove the /courses/
		String courseName = path.substring(9);
		if (courseName.charAt(0) == '.')
		{ // hidden file or directory, skip
			return "";
		}
		if (courseName.indexOf("/") < 0)
		{ // not a directory, skip
			return "";
		}
		// next segment in the path should be the course name
		courseName = courseName.substring(0, courseName.indexOf("/"));
		return courseName;
	}
	
	/**
	 * Load all files that the given user needs to tag.
	 * @param userid
	 * @return
	 * @throws InaccessibleDbException
	 */
	private HashMap<String, CourseFiles> loadAll(String userid) throws InaccessibleDbException
	{
		Connection conn = null;

		HashMap<String, CourseFiles> ret = new HashMap<String, CourseFiles>();
		try 
		{
			conn = cm.getConnection();
			String query = "SELECT course, filepath FROM "+ TABLENAME +" WHERE userid=?";
	        PreparedStatement stmt = conn.prepareStatement(query);
			stmt.setString(1, userid);
	        ResultSet res = stmt.executeQuery();
	
	        while(res.next())
	        {
	        	String courseTitle = res.getString(1);
	        	String path = res.getString(2);
        		String courseName = parseCourseName(path);
	        	String id = courses.getCourseId(courseName);
	        	// STORE PATH FOR RETURN
	        	if (ret.containsKey(id))
	        	{ // add to existing entry
	        		ret.get(id).addFile(path);
	        	}
	        	else
	        	{ // create new entry
	        		CourseFiles cf = new CourseFiles(id, courseName, courseTitle, path);
	        		ret.put(id, cf);
	        	}
	        }
	
	        res.close();
	
	        stmt.close();
		} catch (SQLException e)
		{
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		} catch (PersistenceException e)
		{
			throw new InaccessibleDbException("Unable to read from db", e);
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn);
		}
		
		return ret;
	}
	
	/**
	 * Pagination helper to split up an ArrayList of FilePaths into pages.
	 * 
	 * @param cf
	 * @param page
	 * @return
	 */
	private CourseFiles splitToPage(CourseFiles cf, int page)
	{
		int numPages = (int) Math.ceil(cf.files.size() / (double) ENTRYPERPAGE);
		
		if (page < 1 || page > numPages)
		{ // trying to get a non-existent page, just return the first page
			return splitToPage(cf, 1);
		}
		
		int toIndex = (page * ENTRYPERPAGE);
		int fromIndex = toIndex - ENTRYPERPAGE;
		
		if (toIndex > cf.files.size())
		{ // prevent out of bounds exceptions
			toIndex = cf.files.size();
		}
		
		cf.files = new ArrayList<FilePath>(cf.files.subList(fromIndex, toIndex));
		cf.page = page;
		cf.numPages = numPages;
		
		return cf;
	}
}
