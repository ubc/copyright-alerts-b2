package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;

public class QueueTable
{
	private final static String TABLENAME = "ubc_ctlt_ca_queue";
	public final static int LOADNUM = 500;
	
	/**
	 * Add an array of paths of files to be processed to the queue.
	 * @param paths
	 * @throws InaccessibleDbException 
	 */
	public void add(ArrayList<String> paths) throws InaccessibleDbException
	{
		// Not sure if cm can be made into a private field, but it looks like connections have to be released or Oracle
		// will not be happy
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		// WARNING:
		// For some reason, batch operations instantly crashes the Oracle listener, so we're
		// not going to use batch operations and hope that we don't lose too much speed over this.
		// TODO: Revisit batch operations later when I have more time
		String query = "insert into "+ TABLENAME +" (pk1, filepath) values ("+ TABLENAME +"_seq.nextval, ?)";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);

			for (String path : paths)
			{
				stmt.setString(1, path);
				stmt.executeUpdate();
			}
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

	public ArrayList<String> load() throws InaccessibleDbException 
	{
		return load(LOADNUM);
	}

	/**
	 * Get the next set of files paths to be processed.
	 * Load num rows from the queue. Remember to call pop() to read the next batch off the queue.
	 * 
	 * @param num
	 * @return
	 * @throws InaccessibleDbException 
	 */
	public ArrayList<String> load(int num) throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;

		ArrayList<String> ret = new ArrayList<String>();
		try 
		{
			conn = cm.getConnection();
			// note that there's no order guarantee from just select rownum statements, so have to use the order by subquery
			// to impose a repeatable order on the return results
			String query = "SELECT filepath FROM (SELECT * FROM "+ TABLENAME +" ORDER BY pk1) WHERE rownum <= " + num;
	        PreparedStatement queryCompiled = conn.prepareStatement(query);
	        ResultSet res = queryCompiled.executeQuery();
	
	        while(res.next())
	        {
	        	ret.add(res.getString(1)); // store the path for return
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

        return ret;
	}
	
	public void pop() throws InaccessibleDbException
	{
		pop(LOADNUM);
	}

	/**
	 * Remove num rows from the queue table.
	 * @param num
	 * @throws InaccessibleDbException 
	 */
	public void pop(int num) throws InaccessibleDbException 
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		try
		{
			conn = cm.getConnection();
			String query = "DELETE FROM "+ TABLENAME +" WHERE pk1 IN (SELECT pk1 FROM (SELECT * FROM "+ TABLENAME +" ORDER BY pk1) WHERE rownum <= "+ num +")";
			PreparedStatement deleteQuery = conn.prepareStatement(query); // don't need prepared statement for a single query, but can't be bothered to change
			deleteQuery.executeUpdate();
			deleteQuery.close();
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
	
}
