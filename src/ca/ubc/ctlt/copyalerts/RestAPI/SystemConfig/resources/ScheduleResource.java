package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.io.IOException;
import org.quartz.SchedulerException;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.scheduler.SchedulerManager;

public class ScheduleResource extends ServerResource
{
	private SavedConfiguration config;
	@Override
	protected void doInit() throws ResourceException
	{
		config = SavedConfiguration.getInstance();
		super.doInit();
	}

	@Get("json")
	public JsonRepresentation getSchedule()
	{	
		return new JsonRepresentation(config.toJson());
	}
	
	@Post("json")
	public JsonRepresentation saveSchedule(JsonRepresentation data)
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
	    return getSchedule();
	}
	
}
