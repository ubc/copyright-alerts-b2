package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.quartz.SchedulerException;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.Protocol;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.SyncStatus;
import ca.ubc.ctlt.copyalerts.configuration.HostResolver;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.scheduler.SchedulerManager;

public class ScheduleResource extends ServerResource
{
	private SavedConfiguration config;
	private HostsTable hostTable = null;
	

	@Override
	protected void doInit() throws ResourceException
	{
		config = SavedConfiguration.getInstance();
		try
		{
			hostTable = new HostsTable();
		} catch (InaccessibleDbException e)
		{
			throw new ResourceException(e);
		}
		super.doInit();
	}

	@Get("json")
	public JsonRepresentation getMetadata()
	{	
		return new JsonRepresentation(config.toJson());
	}
	
	@Post("json")
	public JsonRepresentation saveMetadata(JsonRepresentation data)
	{
		SyncStatus ret;
	    try
		{
			String json = data.getText();
		    config.fromJson(json);
		    SchedulerManager.getInstance().updateScheduler();
		    ret = syncNodes();
		} catch (SchedulerException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (IOException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (InaccessibleDbException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
	    // return the new config to caller
	    // MUST RETURN NEW CONFIG, CAN'T RETURN FAILURE STATUS, NOW WHAT?!
	    return new JsonRepresentation(config.toJson(ret));
	}
	
	private SyncStatus syncNodes() throws InaccessibleDbException
	{
		hostTable.loadHosts();
		HashMap<String, Boolean> hosts = hostTable.getHosts();
		String ownHost = HostResolver.getHostname();
		SyncStatus ret = new SyncStatus();
		
		for (Entry<String, Boolean> entry : hosts.entrySet())
		{
			String host = entry.getKey();
			if (host.equals(ownHost)) continue; // skip if own hostname
			String targetUrl = "http://" + host + "/webapps/ubc-copyright-alerts-BBLEARN/systemconfig/sync";
			Client client = new Client(new Context(), Protocol.HTTPS);
			ClientResource resource = new ClientResource(targetUrl);
			resource.setNext(client);
			try
			{
				resource.get();
			} catch (ResourceException e)
			{
				e.printStackTrace();
				ret.syncFailure.add(host);
				continue;
			}
			ret.syncSuccess.add(host);
		}
		
		return ret;
	}
}
