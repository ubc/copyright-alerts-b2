package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import org.quartz.UnableToInterruptJobException;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.scheduler.SchedulerManager;

public class StatusResource extends ServerResource
{
	private HostsTable hostTable = null;

	@Get("json")
	public Representation getStatusDisplay()
	{
		String action = (String) getRequestAttributes().get("action");
		if (action.equals("status"))
		{
			try
			{
				if (hostTable == null) hostTable = new HostsTable(); // initialize it if needed
				hostTable.loadHosts();
				return new JsonRepresentation(hostTable.toStatusJson());
			} catch (InaccessibleDbException e)
			{
				e.printStackTrace();
				getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
				return null;
			}
		}
		else if (action.equals("stop"))
		{
			try
			{
				SchedulerManager.getInstance().interrupt();
				return null;
			} catch (UnableToInterruptJobException e)
			{
				e.printStackTrace();
				getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
				return null;
			}
		}
		else
		{
			getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
	}
}