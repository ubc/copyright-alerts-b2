package ca.ubc.ctlt.copyalerts.systemconfig.api;

import java.io.IOException;

import org.quartz.SchedulerException;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import blackboard.platform.plugin.PlugInException;

import ca.ubc.ctlt.copyalerts.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.systemconfig.scheduler.SchedulerManager;

public class ScheduleAPI extends ServerResource
{
	private SavedConfiguration config = new SavedConfiguration();

	@Override
	protected void doInit() throws ResourceException
	{
		try
		{
			config.load();
		} catch (PlugInException e)
		{
			e.printStackTrace();
			throw new ResourceException(e);
		} catch (IOException e)
		{
			e.printStackTrace();
			throw new ResourceException(e);
		}
		super.doInit();
	}

	@Get("json")
	public JsonRepresentation getMetadata()
	{	
		try
		{
			config.load();
		} catch (PlugInException e)
		{
			e.printStackTrace();
			throw new ResourceException(e);
		} catch (IOException e)
		{
			e.printStackTrace();
			throw new ResourceException(e);
		}
		return new JsonRepresentation(config.toJson());
	}
	
	@Post("json")
	public JsonRepresentation saveMetadata(JsonRepresentation data)
	{
	    try
		{
			String json = data.getText();
		    config.fromJson(json);
		    SchedulerManager.getInstance().updateScheduler();
		} catch (SchedulerException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (PlugInException e)
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
	    // return the new config to caller
	    return getMetadata();
	}
}
