package ca.ubc.ctlt.copyalerts.db;

import blackboard.base.AppVersion;
import blackboard.base.InitializationException;
import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;
import blackboard.db.DataStoreDescriptor;

import java.util.Properties;

public class DbInit
{
	private static ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
	
	public static ConnectionManager getConnectionManager()
	{
		return cm;
	}

	/**
	 * Return a connection manager based on query table name. If the table is in BBLEARN_cms_doc, we need a non-default
	 * manager as it is in different database for PostgreSQL. As it doesn't allow to query cross database in PostgreSQL.
	 *
	 * @param tableName table name to query
	 * @return Connection Manager
     */
	public static ConnectionManager getConnectionManager(String tableName) {
		String[] names = tableName.split("\\.");
		if (BbDatabase.getDefaultInstance().getDatabaseType().equals("postgresql")
				&& names.length > 1
				&& names[0].toLowerCase().equals("bblearn_cms_doc")) {
			// register BBLEARN_cms_doc as it is not available by default. Note this is only applicable to PostgreSQL
			// which is used in developer VM. In production, it should always use Oracle and default ConnectionManager
			DataStoreDescriptor def = BbDatabase.getDefaultInstance().getDescriptor();
			DataStoreDescriptor desc = new DataStoreDescriptor();
			desc.setDbHost("localhost");
			desc.setDbName("BBLEARN_cms_doc");
			desc.setDbUser("BBLEARN_cms_doc");
			desc.setDbPass("pAssw0rd");
			desc.setDbPort(5432);
			desc.setDriver("org.postgresql.Driver");
			desc.setKey("BBLEARN_cms_doc");
			desc.setIsDefault(false);
			desc.setPoolClassName("blackboard.db.impl.TomcatConnectionPool");
			desc.setAppVersion(new AppVersion("bb5", "postgresql", "25"));
			desc.setPoolProps(def.getPoolProps());
			desc.setDriverProps(def.getDriverProps());
			try {
				BbDatabase db = BbDatabase.registerContext(desc);
				return db.getConnectionManager();
			} catch (InitializationException e) {
				e.printStackTrace();
			}
		}

		return cm;
	}
	
}
