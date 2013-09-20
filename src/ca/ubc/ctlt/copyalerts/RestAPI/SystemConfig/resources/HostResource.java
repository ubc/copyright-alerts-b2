package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.io.IOException;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import com.google.gson.Gson;

import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.jsonintermediate.HostOptions;

public class HostResource extends ServerResource
{
	private HostsTable hostTable = null;
	@Override
	protected void doInit() throws ResourceException
	{
		try
		{
			hostTable = new HostsTable();
		} catch (InaccessibleDbException e)
		{
			e.printStackTrace();
			throw new ResourceException(e);
		}
	}
	
	@Get("json")
	public JsonRepresentation getHosts()
	{
		try
		{
			hostTable.load();
		} catch (InaccessibleDbException e)
		{
			e.printStackTrace();
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
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (IOException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
		return getHosts();
	}
}
