package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import blackboard.persist.PersistenceRuntimeException;
import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.QueueTable;
import ca.ubc.ctlt.copyalerts.db.entities.File;
import ca.ubc.ctlt.copyalerts.db.entities.Host;
import ca.ubc.ctlt.copyalerts.db.entities.QueueItem;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.configuration.HostResolver;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

import blackboard.db.BbDatabase;
import blackboard.db.ConnectionManager;
import blackboard.db.ConnectionNotAvailableException;

public class ResetResource extends ServerResource
{
	private final static Logger logger = LoggerFactory.getLogger(ServerResource.class);
	
	@Get("json")
	public JsonRepresentation resetDatabase()
	{
		String[] tables = {File.TABLE_NAME, Host.TABLE_NAME, QueueItem.TABLE_NAME};
		// delete all entries from all our custom tables
		ConnectionManager cm = BbDatabase.getDefaultInstance().getConnectionManager();
		Connection conn = null;
		Statement stmt;
		try
		{
			conn = cm.getConnection();
			for (String table : tables)
			{
				String query = "DELETE FROM " + table;
				stmt = conn.createStatement();
				stmt.executeUpdate(query);
			}
		} catch (SQLException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (ConnectionNotAvailableException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
		finally
		{
			if (conn != null) cm.releaseConnection(conn); // MUST release connection or we'll exhaust connection pool
		}
		
		// re-add this host to the database
		HostsTable hostTable;
		try
		{
			SavedConfiguration config = SavedConfiguration.getInstance();
			config.reset();
			hostTable = new HostsTable();
			// store this host into the hosts table
			String hostname = HostResolver.getHostname();
			if (!hostTable.hasHost(hostname))
			{
				hostTable.addHost(hostname);
			}
		} catch (PersistenceRuntimeException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
		
		return new JsonRepresentation("{\"status\":\"successful\"}");
	}

}