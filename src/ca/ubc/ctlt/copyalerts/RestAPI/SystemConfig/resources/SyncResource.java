package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.io.IOException;

import org.quartz.SchedulerException;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.scheduler.SchedulerManager;

public class SyncResource extends ServerResource
{
	private SavedConfiguration config;
	
	@Override
	protected void doInit() throws ResourceException
	{
		config = SavedConfiguration.getInstance();
		super.doInit();
	}
	
	@Get("json")
	public JsonRepresentation sync()
	{	
		System.out.println("Copyright Alerts Config Sync Request");
		try
		{
			config.load();
			SchedulerManager.getInstance().updateScheduler();
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
		} catch (SchedulerException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
		return new JsonRepresentation("{\"yay\":1}");
	}
}
