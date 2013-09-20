package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map.Entry;

import org.joda.time.Duration;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.HostResolver;
import ca.ubc.ctlt.copyalerts.jsonintermediate.HostOptions;

import com.google.gson.Gson;

import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.platform.vxi.service.VirtualSystemException;

public class HostsTable
{
	public final static String STATUS_RUNNING = "running";
	public final static String STATUS_STOPPED = "stopped";
	public final static String STATUS_LIMIT = "limit";
	public final static String STATUS_ERROR = "error";
	
	public final static String STATUS_RUNNING_KEY = "status";
	public final static String STATUS_RUNTIME_KEY = "runtime";
	public final static String STATUS_START_KEY = "runstart";
	public final static String STATUS_END_KEY = "runend";
	public final static String STATUS_CURHOST_KEY = "host";
	public final static String STATUS_LEADHOST_KEY = "leader";

	private final static String TABLENAME = "ubc_ctlt_ca_hosts";
	
	// keeps track of which nodes are running a copy of this building block so we can determine
	// which node should run the file indexing job
	private HashMap<String, Boolean> hosts = new HashMap<String, Boolean>();
	
	public HostsTable() throws InaccessibleDbException
	{
		load();
	}

	/**
	 * Add a new host.
	 * 
	 * @param host
	 * @param leader
	 * @throws InaccessibleDbException 
	 */
	public void add(String host, boolean leader) throws InaccessibleDbException
	{
		if (hosts.containsKey(host))
		{ // entry already exists
			return;
		}
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
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
	public void add(String host) throws InaccessibleDbException
	{
		add(host, false);
	}
	
	/**
	 * Delete the entry in the database corresponding to the given host.
	 * @param host
	 * @throws InaccessibleDbException
	 */
	public void delete(String host) throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		String query = "delete from "+ TABLENAME +" where host='"+ host +"'";
		Statement stmt;
		try
		{
			conn = cm.getConnection();
			stmt = conn.createStatement();
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
	 * Load existing hosts into the database.
	 * 
	 * @throws InaccessibleDbException
	 */
	public void load() throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		try 
		{
			conn = cm.getConnection();
			// note that there's no order guarantee from just select rownum statements, so have to use the order by subquery
			// to impose a repeatable order on the return results
			String query = "SELECT * FROM "+ TABLENAME;
	        PreparedStatement queryCompiled = conn.prepareStatement(query);
	        ResultSet res = queryCompiled.executeQuery();
	        hosts.clear();
	
	        while(res.next())
	        { // 1: pk1, 2: host, 3: leader, should be order of the columns returned

	        	hosts.put(res.getString(2), res.getBoolean(3));
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
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
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
			e.printStackTrace();
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
	public boolean contains(String host)
	{
		return hosts.containsKey(host);
	}
	
	/**
	 * Save the run stats to the database
	 * @throws InaccessibleDbException 
	 */
	public void setRunStats(String host, String status, Timestamp start, Timestamp end) throws InaccessibleDbException
	{
		if (!hosts.containsKey(host))
		{ // invalid entry
			return;
		}
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
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
	 * Return a json map that lists all the hosts available and the name of the current leader.
	 * @return
	 */
	public String toOptionsJson()
	{
		Gson gson = new Gson();
		HostOptions ret = new HostOptions();
		ret.leader = getLeader();
		ret.options = hosts.keySet();
		ret.alt = HostResolver.getAltHostnames();
		return gson.toJson(ret);
	}
	
	public String toStatusJson() throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		try 
		{
			String hostname = getLeader();
			conn = cm.getConnection();
			String status = STATUS_STOPPED;
			Timestamp start = new Timestamp(0);
			Timestamp end = new Timestamp(0);

			// note that there's no order guarantee from just select rownum statements, so have to use the order by subquery
			// to impose a repeatable order on the return results
			if (!hostname.isEmpty())
			{
				String query = "SELECT "+ STATUS_RUNNING_KEY +", "+ STATUS_START_KEY +", "+ STATUS_END_KEY +" FROM "+ TABLENAME + " WHERE host='" + hostname + "'"; 
		        PreparedStatement queryCompiled = conn.prepareStatement(query);
		        ResultSet res = queryCompiled.executeQuery();
		
		        while(res.next())
		        { // 1: status, 2: start, 3: end, should be order of the columns returned
		        	status = res.getString(1);
		        	start = res.getTimestamp(2);
		        	end = res.getTimestamp(3);
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
}
