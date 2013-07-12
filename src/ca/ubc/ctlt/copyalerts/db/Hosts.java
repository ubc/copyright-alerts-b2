package ca.ubc.ctlt.copyalerts.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;

import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;

public class Hosts
{
	// keeps track of which nodes are running a copy of this building block so we can determine
	// which node should run the file indexing job
	private HashMap<String, Boolean> hosts = new HashMap<String, Boolean>();

	/**
	 * Add a new host.
	 * 
	 * @param host
	 * @param leader
	 */
	public void add(String host, boolean leader)
	{
		// need to store all pk, host, and leaders
		// - instead of a full Host object with those fields, for now just need a map of host/pk, and a leader string
		// can only have 1 leader that needs to be auto-elected if there is no current leader
		// how to make sure that current leader is still online?
		// how to makes sure that non-leaders are still online?
		
		// actually sounds pretty complicated, no guarantee that one node can ping another node yet, so let's work on something else
		/*
		if (hosts.containsKey(host))
		{

		}
		boolean saveResult = true ;
		StringBuffer queryString = new StringBuffer("");
        ConnectionManager cManager = null;
        Connection conn = null;

        cManager = BbDatabase.getDefaultInstance().getConnectionManager();
        conn = cManager.getConnection();

        queryString.append("INSERT INTO ubc_ctlt_ca_hosts ");
        queryString.append("(pk1, host, leader) ");
        queryString.append(" VALUES (ubc_ctlt_ca_hosts_seq.nextval,?,?) ");


        PreparedStatement insertQuery = conn.prepareStatement(queryString.toString());
        insertQuery.setString(1, username);
        insertQuery.setString(2, courseId);
      

        int insertResult = insertQuery.executeUpdate();
        
        if(insertResult != 1){
        	
        	saveResult = false ;
        	
        }
        insertQuery.close();
        */

		hosts.put(host, leader);
	}

	/**
	 * Defaults to adding a non-leader host 
	 * @param host
	 */
	public void add(String host)
	{
		add(host, false);
	}
}
