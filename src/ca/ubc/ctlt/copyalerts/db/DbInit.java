package ca.ubc.ctlt.copyalerts.db;

import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;

public class DbInit
{
	private static ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
	
	public static ConnectionManager getConnectionManager()
	{
		return cm;
	}
	
}
