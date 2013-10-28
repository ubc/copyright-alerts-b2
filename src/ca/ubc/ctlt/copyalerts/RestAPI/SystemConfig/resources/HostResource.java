package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.io.IOException;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.HostOptions;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

public class HostResource extends ServerResource
{
	private final static Logger logger = LoggerFactory.getLogger(HostResource.class);
	private HostsTable hostTable = null;
	@Override
	protected void doInit() throws ResourceException
	{
		try
		{
			hostTable = new HostsTable();
		} catch (InaccessibleDbException e)
		{
			logger.error(e.getMessage(), e);
			throw new ResourceException(e);
		}
	}
	
	@Get("json")
	public JsonRepresentation getHosts()
	{
		try
		{
			hostTable.loadHosts();
		} catch (InaccessibleDbException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
		return new JsonRepresentation(hostTable.toOptionsJson());
	}
	
	@Post("json")
	public JsonRepresentation saveHostLeader(JsonRepresentation data)
	{			
    	try
		{
			String json = data.getText();
		    // parse the json string and save the new host leader
	    	Gson gson = new Gson();
	    	HostOptions host = gson.fromJson(json, HostOptions.class);
			hostTable.setLeader(host.leader);
		} catch (InaccessibleDbException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (IOException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
		return getHosts();
	}
}
