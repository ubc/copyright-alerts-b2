package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;

public class FilesTable
{
	private final static String TABLENAME = "ubc_ctlt_ca_files";

	public void add(ArrayList<UserFilePair> entries) throws InaccessibleDbException
	{
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		String query = "insert into "+ TABLENAME +" (pk1, username, filepath) values ("+ TABLENAME +"_seq.nextval, ?, ?)";
		PreparedStatement stmt;
		try
		{
			conn = cm.getConnection();
			// convert the query string into a compiled statement for faster execution
			stmt = conn.prepareStatement(query);

			for (UserFilePair e : entries)
			{
				stmt.setString(1, e.getUsername());
				stmt.setString(2, e.getPath());
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
	
	public void deleteAll() throws InaccessibleDbException
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
