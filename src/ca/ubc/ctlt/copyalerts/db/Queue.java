package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;

public class Queue
{
	private final static String TABLENAME = "ubc_ctlt_ca_queue";
	private final static int LOADNUM = 500;
	Connection conn;
	
	public Queue() throws ConnectionNotAvailableException
	{
		ConnectionManager m = BbDatabase.getDefaultInstance().getConnectionManager();
        conn = m.getConnection();
	}
	
	/**
	 * Add an array of paths of files to be processed to the queue.
	 * @param paths
	 * @throws SQLException 
	 */
	public void add(ArrayList<String> paths) throws SQLException
	{
		String query = "insert into ubc_ctlt_ca_queue (pk1, filepath) values (ubc_ctlt_ca_queue_seq.nextval, ?)";
		/*
		PreparedStatement stmt = conn.prepareStatement(query);
		stmt.setString(1, paths.get(0));
		stmt.executeUpdate();
		stmt.close();
		*/
		
		conn.setAutoCommit(false);
		try
		{
			PreparedStatement insertQuery = conn.prepareStatement(query);
			for (String path : paths)
			{
				insertQuery.setString(1, path);
				insertQuery.addBatch();
			}
			insertQuery.executeBatch();
			conn.commit();
			insertQuery.close();
		} catch (SQLException e)
		{
			e.printStackTrace();
			conn.rollback();
		} finally {
			conn.setAutoCommit(true); // make sure to re-enable auto commit after failure
		}
	}

	public ArrayList<String> load() throws SQLException 
	{
		return load(LOADNUM);
	}

	/**
	 * Get the next set of files paths to be processed.
	 * Load num rows from the queue. Remember to call pop() to read the next batch off the queue.
	 * 
	 * @param num
	 * @return
	 * @throws SQLException
	 */
	public ArrayList<String> load(int num) throws SQLException
	{
		System.out.println("Loading");
		ArrayList<String> ret = new ArrayList<String>();
        String query = "SELECT filepath FROM "+ TABLENAME +" WHERE rownum <= " + num;
        PreparedStatement queryCompiled = conn.prepareStatement(query, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        ResultSet res = queryCompiled.executeQuery();

        while(res.next())
        {
        	System.out.println("Found: " + res.getString(1));
        	ret.add(res.getString(1)); // store the path for return
        }

        res.close();

        queryCompiled.close();
		System.out.println("Loading done");

        return ret;
	}
	
	public void pop() throws SQLException
	{
		pop(LOADNUM);
	}

	/**
	 * Remove num rows from the queue table.
	 * @param num
	 * @throws SQLException
	 */
	public void pop(int num) throws SQLException
	{
		String query = "DELETE FROM "+ TABLENAME +" where rownum <= " + num;
		PreparedStatement deleteQuery = conn.prepareStatement(query);
		deleteQuery.executeUpdate();
		deleteQuery.close();
	}

}
