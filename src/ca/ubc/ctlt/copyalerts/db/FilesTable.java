package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import blackboard.cms.filesystem.CSFile;
import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.persist.Id;

public class FilesTable
{
	private final static String TABLENAME = "ubc_ctlt_ca_files";
	
	public static ArrayList<String> load(String userid) throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;

		ArrayList<String> ret = new ArrayList<String>();
		try 
		{
			conn = cm.getConnection();
			// note that there's no order guarantee from just select rownum statements, so have to use the order by subquery
			// to impose a repeatable order on the return results
			String query = "SELECT filepath FROM "+ TABLENAME +" WHERE userid=?";
	        PreparedStatement stmt = conn.prepareStatement(query);
			stmt.setString(1, userid);
	        ResultSet res = stmt.executeQuery();
	
	        while(res.next())
	        {
	        	ret.add(res.getString(1)); // store the path for return
	        }
	
	        res.close();
	
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
			if (conn != null) cm.releaseConnection(conn);
		}

		
		return ret;
	}

	public static void add(CSFile file, ArrayList<Id> users) throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		String query = "insert into "+ TABLENAME +" (pk1, userid, filepath) values ("+ TABLENAME +"_seq.nextval, ?, ?)";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);

			for (Id e : users)
			{
				stmt.setString(1, e.toExternalString());
				stmt.setString(2, file.getFullPath());
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
	
	public static void deleteAll() throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
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
	
}
