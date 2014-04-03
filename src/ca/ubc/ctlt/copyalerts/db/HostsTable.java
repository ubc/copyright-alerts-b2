package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map.Entry;

import org.joda.time.Duration;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.HostOptions;
import ca.ubc.ctlt.copyalerts.configuration.HostResolver;

import com.google.gson.Gson;

import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.platform.vxi.service.VirtualSystemException;

public class HostsTable
{
	private final static Logger logger = LoggerFactory.getLogger(HostsTable.class);
	
	// all the possible values for the "status" field
	public final static String STATUS_RUNNING = "running";
	public final static String STATUS_STAGE_QUEUE = "queue";
	public final static String STATUS_STAGE_NEWFILES = "newfiles";
	public final static String STATUS_STAGE_UPDATE = "update";
	public final static String STATUS_STOPPED = "stopped";
	public final static String STATUS_LIMIT = "limit";
	public final static String STATUS_ERROR = "error";
	
	// the column names for the hosts table
	public final static String STATUS_RUNNING_KEY = "status"; // running status of this host
	public final static String STATUS_STAGE_KEY = "stage"; // at which stage we're at
	public final static String STATUS_RUNTIME_KEY = "runtime"; // how long did the last run take
	public final static String STATUS_START_KEY = "runstart"; // when did the last run start
	public final static String STATUS_END_KEY = "runend"; // when did the last run end
	public final static String STATUS_CURHOST_KEY = "host"; // the host name that we use to identify this node
	public final static String STATUS_LEADHOST_KEY = "leader"; // whether this host is selected to run alerts generation

	public final static String QUEUE_OFFSET_KEY = "queue_offset"; // number of files the queue generation stage has gone through this run
	public final static String LAST_QUEUE_FILEID_KEY = "last_queue_fileid"; // what was the last file the queue generator committed
																			// if 0, then queue generation is finished
	public final static String FILES_OFFSET_KEY = "files_offset";
	public final static String LAST_FILES_PK1_KEY = "last_files_pk1";

	private final static String TABLENAME = "ubc_ctlt_ca_hosts";
	
	// keeps track of which nodes are running a copy of this building block so we can determine
	// which node should run the file indexing job
	private HashMap<String, Boolean> hosts = new HashMap<String, Boolean>();
	
	private ConnectionManager cm = DbInit.getConnectionManager();

	public HostsTable() throws InaccessibleDbException
	{
		loadHosts();
	}

	/**
	 * Add a new host.
	 * 
	 * @param host
	 * @param leader
	 * @throws InaccessibleDbException 
	 */
	public void addHost(String host, boolean leader) throws InaccessibleDbException
	{
		if (hosts.containsKey(host))
		{ // entry already exists
			return;
		}
		Connection conn = null;
		String query = "insert into "+ TABLENAME +" (pk1, host, leader) values ("+ TABLENAME +"_seq.nextval, ?, ?)";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);

			stmt.setString(1, host);
			stmt.setBoolean(2, leader);
			stmt.executeUpdate();
			stmt.close();
			hosts.put(host, leader);	
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

	/**
	 * Defaults to adding a non-leader host 
	 * @param host
	 * @throws InaccessibleDbException 
	 */
	public void addHost(String host) throws InaccessibleDbException
	{
		addHost(host, false);
	}
	
	/**
	 * Delete the entry in the database corresponding to the given host.
	 * @param host
	 * @throws InaccessibleDbException
	 */
	public void deleteHost(String host) throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "delete from "+ TABLENAME +" where host = ?";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			stmt = conn.prepareStatement(query);
			stmt.setString(1, host);
			stmt.executeUpdate(query);
			hosts.remove(host);
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
	
	/**
	 * Load existing hosts from the database.
	 * 
	 * @throws InaccessibleDbException
	 */
	public void loadHosts() throws InaccessibleDbException
	{
		Connection conn = null;
		try 
		{
			conn = cm.getConnection();
			String query = "SELECT host, leader FROM "+ TABLENAME;
	        PreparedStatement queryCompiled = conn.prepareStatement(query);
	        ResultSet res = queryCompiled.executeQuery();
	        hosts.clear();
	
			while (res.next())
			{
				hosts.put(res.getString(1), res.getBoolean(2));
			}
	
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
	}
	
	/**
	 * Get the current leader that is supposed to be executing the indexing job.
	 * @return The current leader of the round if there is one, empty otherwise
	 */
	public String getLeader()
	{
		for (Entry<String, Boolean> e : hosts.entrySet())
		{
			if (e.getValue())
			{
				return e.getKey();
			}
		}
		return "";
	}
	
	/**
	 * Set the current leader to the given host.
	 * @param host String identifying the hostname that is supposed to be executing then indexing job.
	 * @throws InaccessibleDbException 
	 */
	public void setLeader(String host) throws InaccessibleDbException
	{
		if (!hosts.containsKey(host) || hosts.get(host))
		{ // do nothing if we don't have the host entry or if the host is already the leader
			return;
		}
		String formerLeader = getLeader();
		// demote the current leader
		// promote the new leader
		Connection conn = null;
		String query = "UPDATE "+ TABLENAME +" SET leader=? WHERE host=?";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			// demote current leader
			stmt.setString(1, "0");
			stmt.setString(2, formerLeader);
			stmt.executeUpdate();
			// promote the new leader
			stmt.setString(1, "1");
			stmt.setString(2, host);
			stmt.executeUpdate();
			stmt.close();
			// update in memory mapping
			hosts.put(formerLeader, false);
			hosts.put(host, true);
		} catch (SQLException e)
		{
			logger.error(e.getMessage(), e);
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
	
	/**
	 * Returns true if host already exists in the database, false otherwise.
	 * @param host
	 * @return
	 */
	public boolean hasHost(String host)
	{
		return hosts.containsKey(host);
	}
	
	/**
	 * Save the run stats to the database
	 * @throws InaccessibleDbException 
	 */
	public void saveRunStats(String host, String status, Timestamp start, Timestamp end) throws InaccessibleDbException
	{
		if (!hosts.containsKey(host))
		{ // invalid entry
			return;
		}
		Connection conn = null;
		String query = "update "+ TABLENAME +" set "+ STATUS_RUNNING_KEY +"=?, "+ STATUS_START_KEY +"=?, "+ STATUS_END_KEY +"=? where host='" + host + "'";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			stmt.setString(1, status);
			stmt.setTimestamp(2, start);
			stmt.setTimestamp(3, end);
			stmt.executeUpdate();
			stmt.close();
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
	
	/**
	 * Load the schedule & metadata template configuration
	 * @return
	 * @throws InaccessibleDbException 
	 */
	public String loadConfig() throws InaccessibleDbException
	{
		Connection conn = null;
		try 
		{
			conn = cm.getConnection();
			String query = "SELECT config FROM "+ TABLENAME +" WHERE leader = '1'";
			PreparedStatement queryCompiled = conn.prepareStatement(query);
			ResultSet res = queryCompiled.executeQuery();
			String ret = "";
			while(res.next())
			{
				ret = res.getString(1);
				break;
			}
			res.close();
			queryCompiled.close();
			return ret;
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
	}
	
	public void saveConfig(String config) throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "UPDATE "+ TABLENAME +" SET config = ?";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			stmt.setString(1, config);
			stmt.executeUpdate();
			stmt.close();
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
	
	/**
	 * Return a json map that lists all the hosts available and the name of the current leader.
	 * @return
	 */
	public String toOptionsJson()
	{
		Gson gson = new Gson();
		HostOptions ret = new HostOptions();
		ret.leader = getLeader();
		ret.current = HostResolver.getHostname();
		ret.options = hosts.keySet();
		return gson.toJson(ret);
	}
	
	public String toStatusJson() throws InaccessibleDbException
	{
		Connection conn = null;
		try 
		{
			String hostname = getLeader();
			conn = cm.getConnection();
			String status = STATUS_STOPPED;
			Timestamp start = new Timestamp(0);
			Timestamp end = new Timestamp(0);
			String stage = STATUS_STAGE_QUEUE;

			// note that there's no order guarantee from just select rownum statements, so have to use the order by subquery
			// to impose a repeatable order on the return results
			if (!hostname.isEmpty())
			{
				String query = "SELECT "+ STATUS_RUNNING_KEY +", "+ STATUS_START_KEY +", "+ STATUS_END_KEY + ", " + STATUS_STAGE_KEY + " FROM "+ TABLENAME + " WHERE host = ?"; 
		        PreparedStatement queryCompiled = conn.prepareStatement(query);
		        queryCompiled.setString(1, hostname);
		        ResultSet res = queryCompiled.executeQuery();
		
		        while(res.next())
		        { // 1: status, 2: start, 3: end, should be order of the columns returned
		        	status = res.getString(1);
		        	start = res.getTimestamp(2);
		        	end = res.getTimestamp(3);
		        	stage = res.getString(4);
		        }
		        res.close();
		        queryCompiled.close();
			}
			
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a");
	        HashMap<String, String> ret = new HashMap<String, String>();
	        ret.put(STATUS_RUNNING_KEY, status);
	        ret.put(STATUS_START_KEY, "-");
	        ret.put(STATUS_RUNTIME_KEY, "-");
	        ret.put(STATUS_END_KEY, "-");
	        ret.put(STATUS_STAGE_KEY, stage);
	        if (start != null && start.getTime() > 0)
	        {
		        ret.put(STATUS_START_KEY, dateFormat.format(start));
	        }
	        if (end != null && end.getTime() > 0)
	        {
		        ret.put(STATUS_RUNTIME_KEY, getRuntime(start, end));
		        ret.put(STATUS_END_KEY, dateFormat.format(end));
	        }
	        ret.put(STATUS_LEADHOST_KEY, hostname);

	        ret.put(STATUS_CURHOST_KEY, HostResolver.getHostname());
	        Gson gson = new Gson();
	        return gson.toJson(ret);
		} catch (SQLException e)
		{
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		} catch (VirtualSystemException e)
		{
			throw new InaccessibleDbException("Unable to access virtual system", e);
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn);
		}
	}
	
	private String getRuntime(Timestamp start, Timestamp end)
	{
		Duration duration = new Duration(end.getTime() - start.getTime()); // in milliseconds
		PeriodFormatter formatter = new PeriodFormatterBuilder()
			.appendDays()
			.appendSuffix("d ")
			.appendHours()
			.appendSuffix("h ")
			.appendMinutes()
			.appendSuffix("m ")
			.appendSeconds()
			.appendSuffix("s")
			.toFormatter();
		String formattedDuration = formatter.print(duration.toPeriod());
		if (formattedDuration.isEmpty())
		{
			return "0s";
		}
		return formattedDuration;
	}

	/**
	 * @return the hosts
	 */
	public HashMap<String, Boolean> getHosts()
	{
		return hosts;
	}
	
	/**
	 * Save the data necessary for resuming from an interrupted queue generation.
	 * @param queueOffset - How many table entries we've already processed
	 * @param lastQueueFileid - The id of the last file we processed.
	 * @throws InaccessibleDbException
	 */
	public void saveQueueResumeData(long queueOffset, long lastQueueFileid) throws InaccessibleDbException
	{
		saveResumeData(QUEUE_OFFSET_KEY, LAST_QUEUE_FILEID_KEY, queueOffset, lastQueueFileid);
	}
	
	public long getQueueOffset() throws InaccessibleDbException
	{
		return getResumeData(QUEUE_OFFSET_KEY);
	}

	public long getLastQueueFileid() throws InaccessibleDbException
	{
		return getResumeData(LAST_QUEUE_FILEID_KEY);
	}
	
	public void saveFileResumeData(long offset, long pk1) throws InaccessibleDbException
	{
		saveResumeData(FILES_OFFSET_KEY, LAST_FILES_PK1_KEY, offset, pk1);
	}
	
	public long getFilesOffset() throws InaccessibleDbException
	{
		return getResumeData(FILES_OFFSET_KEY);
	}
	
	public long getLastFilesPk1() throws InaccessibleDbException
	{
		return getResumeData(LAST_FILES_PK1_KEY);
	}
	
	public void saveStage(String stage) throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "UPDATE "+ TABLENAME +" SET " + STATUS_STAGE_KEY + "=?";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			stmt.setString(1, stage);
			stmt.executeUpdate();
			stmt.close();
		} catch (SQLException e)
		{
			logger.error(e.getMessage(), e);
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
	
	public String getStage() throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "SELECT "+ STATUS_STAGE_KEY + " FROM " + TABLENAME +" WHERE leader='1'";
		PreparedStatement stmt;
		String ret = "";
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			ResultSet res = stmt.executeQuery();
			res.next();
			ret = res.getString(1);
			stmt.close();
		} catch (SQLException e)
		{
			logger.error(e.getMessage(), e);
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
		}
		return ret;
	}
	
	/**
	 * Generalized method for saving resume data.
	 * @param offsetKey
	 * @param idKey
	 * @param offset
	 * @param id
	 * @throws InaccessibleDbException
	 */
	private void saveResumeData(String offsetKey, String idKey, long offset, long id) throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "UPDATE "+ TABLENAME +" SET " + offsetKey + "=?, " + idKey + "=?";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			stmt.setLong(1, offset);
			stmt.setLong(2, id);
			stmt.executeUpdate();
			stmt.close();
		} catch (SQLException e)
		{
			logger.error(e.getMessage(), e);
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
	
	/**
	 * Generalized method for retriving a single resume data file.
	 * @param fieldKey - name of the column that holdes the data
	 * @return
	 * @throws InaccessibleDbException
	 */
	private long getResumeData(String fieldKey) throws InaccessibleDbException
	{
		Connection conn = null;
		String query = "SELECT "+ fieldKey + " FROM " + TABLENAME +" WHERE leader='1'";
		PreparedStatement stmt;
		long ret = 0;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);
			ResultSet res = stmt.executeQuery();
			res.next();
			ret = res.getLong(1);
			stmt.close();
		} catch (SQLException e)
		{
			logger.error(e.getMessage(), e);
			throw new InaccessibleDbException("Couldn't execute query", e);
		} catch (ConnectionNotAvailableException e)
		{
			throw new InaccessibleDbException("Unable to connect to db", e);
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
		}
		return ret;
	}
}
