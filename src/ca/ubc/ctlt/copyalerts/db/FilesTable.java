package ca.ubc.ctlt.copyalerts.db;

import blackboard.cms.filesystem.CSFile;
import blackboard.persist.Id;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.persist.dao.impl.SimpleDAO;
import blackboard.persist.impl.DeleteQuery;
import blackboard.persist.impl.SimpleCountQuery;
import blackboard.persist.impl.SimpleSelectQuery;
import blackboard.persist.impl.mapping.DbObjectMap;
import blackboard.persist.impl.mapping.annotation.AnnotationMappingFactory;
import blackboard.platform.query.Criteria;
import ca.ubc.ctlt.copyalerts.JsonIntermediate.CourseFiles;
import ca.ubc.ctlt.copyalerts.JsonIntermediate.FileList;
import ca.ubc.ctlt.copyalerts.db.entities.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

public class FilesTable extends SimpleDAO<File>
{
	private static final DbObjectMap FILE_EXT_MAP = AnnotationMappingFactory.getMap(File.class);

	private final static Logger logger = LoggerFactory.getLogger(FilesTable.class);

	public final static String TABLENAME = "ubc_ctlt_ca_files";
	private final static int ENTRYPERPAGE = 25;

	// store mapping of course names to course title, names are id like, title is human readable
	private CourseCache courses = new CourseCache();

	public FilesTable()
	{
		super(FILE_EXT_MAP);
	}

	// have to load paging specially as each course can be paged
	// have a loadCourseFiles or getCourseFiles
	public FileList load(String userid) throws PersistenceException {
		HashMap<String, CourseFiles> ret = loadAll(userid);
		for (Entry<String, CourseFiles> e : ret.entrySet())
		{
			CourseFiles cf = e.getValue();
			cf = splitToPage(cf, 1);
			ret.put(e.getKey(), cf);
		}

		return new FileList(ret, 1, 1);
	}

	public CourseFiles loadCourseFiles(String userid, String courseId, int page) throws PersistenceException {
		HashMap<String, CourseFiles> list = loadAll(userid);
		if (list.containsKey(courseId))
		{
			CourseFiles cf = list.get(courseId);
			return splitToPage(cf, page);
		}
		return null;
	}

	private boolean hasUserId(String userIdStr, List<File> files) {
		for (File f: files) {
			if (f.getUserId().equals(userIdStr)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Adding files to the database in a batch operation in order to get some performance gains from compiled
	 * statements.
	 *
	 * @param filesAndUsers files and users
	 * @throws PersistenceException
	 */
	public void add(Map<CSFile, Set<Id>> filesAndUsers) throws PersistenceException {
		InsertBulkQuery insertQuery = new InsertBulkQuery(getDAOSupport().getMap());
		for (Entry<CSFile, Set<Id>> entry : filesAndUsers.entrySet())
		{
			CSFile file = entry.getKey();
			Set<Id> users = entry.getValue();

			// first, make sure that we're using a valid course
			String courseName = parseCourseName(file.getFullPath());
			if (courseName.isEmpty()) {
				logger.warn("Failed to parse course name in " + file.getFullPath() + "(invalid?), Skipping...");
				continue;
			}
			boolean validCourse = courses.addIfValidCourse(courseName);
			if (!validCourse) {
				logger.warn("Couldn't retrieve course " + courseName + " from cache or database(invalid?), Skipping");
				continue;
			}

			// check duplicates
			String fileId = file.getFileSystemEntry().getEntryID();
			String[] userArray = new String[users.size()];
			int index = 0;
			for (Id userId: users) {
				userArray[index] = userId.toExternalString();
				index++;
			}
			SimpleSelectQuery query = new SimpleSelectQuery(this.getDAOSupport().getMap());
			Criteria criteria = query.getCriteria();
			criteria.add(criteria.and(
					criteria.equal("fileId", file.getFileSystemEntry().getEntryID()),
					criteria.in("userId", (Object[])userArray)
			));
			List<File> files = getDAOSupport().loadList(query);

			int skipped = 0;
			for (Id userId : users) {
				String userIdStr = userId.toExternalString();
				// found duplicate, skip
				if (hasUserId(userIdStr, files)) {
					skipped++;
					continue;
				}

				File f = new File(userIdStr, courses.getCourseTitle(courseName), file.getFullPath(), fileId);
				insertQuery.addObject(f);
			}
			logger.debug("Skipped " +  skipped + " entries as userId and fileId already exists");
		}

		if (insertQuery.getObjectsToInsert().size() > 0) {
			getDAOSupport().execute(insertQuery);
		} else {
			// nothing to insert, close query instead
			insertQuery.close();
		}


//		Connection conn = null;
//		String insertQuery = "insert into "+ TABLENAME +" (pk1, userid, course, filepath, fileid) " +
//				"values ("+ TABLENAME +"_seq.nextval, ?, ?, ?, ?)";
//		String checkDupQuery = "SELECT pk1 FROM "+ TABLENAME +" WHERE fileid = ? AND userid = ?";
//		try
//		{
//			conn = cm.getConnection();
//			// convert the query string into a compiled statement for faster
//			// execution
//			PreparedStatement insertStmt;
//			PreparedStatement checkDupStmt;
//			insertStmt = conn.prepareStatement(insertQuery);
//			checkDupStmt = conn.prepareStatement(checkDupQuery);
//			// process each file
//			for (Entry<CSFile, Set<Id>> entry : filesAndUsers.entrySet())
//			{
//				addFile(entry.getKey(), entry.getValue(), checkDupStmt, insertStmt);
//			}
//			// add all the entries all at once, hopefully it improves performance since it's one query
//			insertStmt.executeBatch();
//			insertStmt.close();
//			checkDupStmt.close();
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		} catch (PersistenceException e)
//		{
//			throw new InaccessibleDbException("Unable to read from db", e);
//		} finally
//		{
//			if (conn != null)
//				cm.releaseConnection(conn); // MUST release connection or we'll
//											// exhaust connection pool
//		}
	}

//	/**
//	 * Does the actual queries that adds new files into the database.
//	 * @param file
//	 * @param users
//	 * @param checkDupStmt
//	 * @param insertStmt
//	 * @throws PersistenceException
//	 * @throws SQLException
//	 */
//	private void addFile(CSFile file, Set<Id> users, PreparedStatement checkDupStmt, PreparedStatement insertStmt)
//			throws PersistenceException, SQLException
//	{
//		// first, make sure that we're using a valid course
//		String courseName = parseCourseName(file.getFullPath());
//		if (courseName.isEmpty())
//			return; // failed to parse course name, so must be invalid
//		boolean validCourse;
//		validCourse = courses.addIfValidCourse(courseName);
//		if (!validCourse)
//			return; // couldn't retrieve course from cache or
//					// database,
//					// so must be invalid
//		String fileId = file.getFileSystemEntry().getEntryID();
//
//		for (Id userId : users)
//		{
//			String userIdStr = userId.toExternalString();
//			// Check to make sure that this entry isn't a duplicate
//			// before saving it. Note that while there is an Oracle "hint" mechanism similar
//			// to MySQL's insert if not exists mechanism, it doesn't perform as well as
//			// checking for existence and then inserting.
//			checkDupStmt.setString(1, fileId);
//			checkDupStmt.setString(2, userIdStr);
//			ResultSet res = checkDupStmt.executeQuery();
//			if (res.next())
//			{ // entry already exists, skip it
//				res.close();
//				continue;
//			}
//			res.close();
//			// entry doesn't exist, so enter it into the database
//			insertStmt.setString(1, userIdStr);
//			insertStmt.setString(2, courses.getCourseTitle(courseName));
//			insertStmt.setString(3, file.getFullPath());
//			insertStmt.setString(4, fileId);
//			insertStmt.addBatch(); // not in database yet! Need to execute batch later.
//		}
//	}

//	public void deleteAll() throws InaccessibleDbException
//	{
//		DeleteQuery query = new DeleteQuery(this.getDAOSupport().getMap());
//		this.getDAOSupport().delete(query);
//		Connection conn = null;
//		String query = "delete from "+ TABLENAME;
//		Statement stmt;
//		try
//		{
//			conn = cm.getConnection();
//			stmt = conn.createStatement();
//			stmt.executeUpdate(query);
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		}
//		finally
//		{
//			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
//		}
//	}

	/**
	 * Given a list of primary keys, remove all files with those primary keys from the table.
	 * @param filesToRemove list of fileIds to remove
	 */
	public void deleteFilesByPk1(Set<Long> filesToRemove)
	{
		logger.debug("Batch Deleting Files From Index.");
		DeleteQuery query = new DeleteQuery(this.getDAOSupport().getMap());
		Criteria criteria = query.getCriteria();
		criteria.add(criteria.in("id", filesToRemove));
		this.getDAOSupport().delete(query);
//		Connection conn = null;
//		String deleteQuery = "DELETE FROM "+ TABLENAME +" WHERE pk1=?";
//		try
//		{
//			conn = cm.getConnection();
//			// convert the query string into a compiled statement for faster
//			// execution
//			PreparedStatement deleteStmt;
//			deleteStmt = conn.prepareStatement(deleteQuery);
//			// process each file
//			for (long filePk1 : filesToRemove)
//			{
//				deleteStmt.setLong(1, filePk1);
//				deleteStmt.addBatch();
//			}
//			// add all the entries all at once, hopefully it improves performance since it's one query
//			deleteStmt.executeBatch();
//			deleteStmt.close();
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		} finally
//		{
//			if (conn != null)
//				cm.releaseConnection(conn); // MUST release connection or we'll
//											// exhaust connection pool
//		}
	}

	public void deleteFile(String path)
	{
		DeleteQuery query = new DeleteQuery(this.getDAOSupport().getMap());
		Criteria criteria = query.getCriteria();
		criteria.add(criteria.equal("filepath", path));
		this.getDAOSupport().delete(query);
//		Connection conn = null;
//		String query = "delete from "+ TABLENAME + " where filepath = ?";
//		try
//		{
//			conn = cm.getConnection();
//			PreparedStatement stmt = conn.prepareStatement(query);
//			stmt.setString(1, path);
//			stmt.executeUpdate();
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		}
//		finally
//		{
//			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
//		}
	}

	// Get the number of entries in this table
	public long getCount()
	{
		long ret = 0;

		SimpleCountQuery query = new SimpleCountQuery(this.getDAOSupport().getMap(), "count");
		try {
			ret = getDAOSupport().loadResult(query, Integer.class);
		} catch (KeyNotFoundException e) {
			e.printStackTrace();
		}

		return ret;

//		Connection conn = null;
//		long ret = 0;
//
//		try
//		{
//			conn = cm.getConnection();
//			String query = "SELECT COUNT(*) FROM " + TABLENAME;
//	        PreparedStatement queryCompiled = conn.prepareStatement(query);
//	        ResultSet res = queryCompiled.executeQuery();
//
//	        res.next();
//        	ret = res.getLong(1);
//
//	        res.close();
//	        queryCompiled.close();
//		} catch (SQLException e)
//		{
//			throw new InaccessibleDbException("Couldn't execute query", e);
//		} catch (ConnectionNotAvailableException e)
//		{
//			throw new InaccessibleDbException("Unable to connect to db", e);
//		}
//		finally
//		{
//			if (conn != null) cm.releaseConnection(conn);
//		}
//
//        return ret;
	}


	/**
	 * Parse out the course name from the full content system path.
	 * @param path file path to parse
	 * @return course name
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
		if (!courseName.contains("/"))
		{ // not a directory, skip
			return "";
		}
		// next segment in the path should be the course name
		courseName = courseName.substring(0, courseName.indexOf("/"));
		return courseName;
	}

	/**
	 * Load all files that the given user needs to tag.
	 * @param userid user id
	 * @return Map of course files
	 */
	private HashMap<String, CourseFiles> loadAll(String userid) throws PersistenceException {
//		Connection conn = null;

		HashMap<String, CourseFiles> ret = new HashMap<>();

		SimpleSelectQuery query = new SimpleSelectQuery(this.getDAOSupport().getMap());
		Criteria criteria = query.getCriteria();
		criteria.add(criteria.equal("userId", userid));
		List<File> files = getDAOSupport().loadList(query);

		for (File file: files) {
			String path = file.getFilePath();
			String courseName = parseCourseName(path);
			String id = courses.getCourseId(courseName);
			if (null == id) {
				// course could be deleted after cache is generated, so skip them for now
				// a clean up process could be in place to clear out the outdated caches
				continue;
			}
			// STORE PATH FOR RETURN
			if (ret.containsKey(id)) {
				// add to existing entry
				ret.get(id).addFile(path);
			} else {
				// create new entry
				CourseFiles cf = new CourseFiles(id, courseName, file.getCourse(), path);
				ret.put(id, cf);
			}
		}
//			conn = cm.getConnection();
//			String query = "SELECT course, filepath FROM "+ TABLENAME +" WHERE userid=?";
//	        PreparedStatement stmt = conn.prepareStatement(query);
//			stmt.setString(1, userid);
//	        ResultSet res = stmt.executeQuery();

//		while(res.next())
//		{
//			String courseTitle = res.getString(1);
//			String path = res.getString(2);
//			String courseName = parseCourseName(path);
//			String id = courses.getCourseId(courseName);
//			if (null == id) {
//				// course could be deleted after cache is generated, so skip them for now
//				// a clean up process could be in place to clear out the outdated caches
//				continue;
//			}
//			// STORE PATH FOR RETURN
//			if (ret.containsKey(id))
//			{ // add to existing entry
//				ret.get(id).addFile(path);
//			}
//			else
//			{ // create new entry
//				CourseFiles cf = new CourseFiles(id, courseName, courseTitle, path);
//				ret.put(id, cf);
//			}
//		}

		return ret;
	}

	/**
	 * Pagination helper to split up an ArrayList of FilePaths into pages.
	 *
	 * @param cf course files
	 * @param page page
	 * @return course files
	 */
	// TODO: refactor to use ORM pagination
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

		cf.files = new ArrayList<>(cf.files.subList(fromIndex, toIndex));
		cf.page = page;
		cf.numPages = numPages;

		return cf;
	}
}
