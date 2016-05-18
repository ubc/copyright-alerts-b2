package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import ca.ubc.ctlt.copyalerts.configuration.HostResolver;
import ca.ubc.ctlt.copyalerts.db.*;
import org.quartz.UnableToInterruptJobException;
import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.Progress;
import ca.ubc.ctlt.copyalerts.scheduler.SchedulerManager;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;

public class StatusResource extends ServerResource
{
	private final static Logger logger = LoggerFactory.getLogger(StatusResource.class);

	private HostsTable hostTable = null;
	private StatusTable statusTable = null;
	private FilesTable fileTable = new FilesTable();
	private QueueTable queueTable = new QueueTable();

	@Get("json")
	public Representation getStatusDisplay()
	{
		String action = (String) getRequestAttributes().get("action");
		if (null == action || action.equals("status"))
		{ // return the current execution status
			try
			{
				if (hostTable == null) hostTable = new HostsTable(); // initialize it if needed
				if (statusTable == null) statusTable = StatusTable.getInstance(); // initialize it if needed
				hostTable.loadHosts();
				return new JsonRepresentation(toStatusJson());
			} catch (InaccessibleDbException e)
			{
				logger.error(e.getMessage(), e);
				getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
				return null;
			}
		}
		else if (action.equals("progress"))
		{ // return the progress report through counts on the file and queue tables
			Progress progress = new Progress();
			Gson gson = new Gson();
			progress.fileCount = fileTable.getCount();
			progress.queueCount = queueTable.getCount();
			return new JsonRepresentation(gson.toJson(progress));
		}
		else if (action.equals("stop"))
		{ // stop the current indexing process, doesn't work if not on same server
			try
			{
				SchedulerManager.getInstance().interrupt();
				return null;
			} catch (UnableToInterruptJobException e)
			{
				logger.error(e.getMessage(), e);
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

	public String toStatusJson() throws InaccessibleDbException
	{
		String hostname = hostTable.getLeader();
		String status = ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_STOPPED;
		Timestamp start = new Timestamp(0);
		Timestamp end = new Timestamp(0);
		String stage = ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_STAGE_QUEUE;

		// note that there's no order guarantee from just select rownum statements, so have to use the order by subquery
		// to impose a repeatable order on the return results
		if (!hostname.isEmpty())
		{
			status = statusTable.getStatus();
			start = statusTable.getStart();
			end = statusTable.getEnd();
			stage = statusTable.getStage();
		}

		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a");
		HashMap<String, String> ret = new HashMap<String, String>();
		ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_RUNNING_KEY, status);
		ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_START_KEY, "-");
		ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_RUNTIME_KEY, "-");
		ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_END_KEY, "-");
		ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_STAGE_KEY, stage);
		if (start != null && start.getTime() > 0)
		{
			ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_START_KEY, dateFormat.format(start));
		}
		if (end != null && end.getTime() > 0)
		{
			ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_RUNTIME_KEY, statusTable.getRuntime(start, end));
			ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_END_KEY, dateFormat.format(end));
		}
		ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_LEADHOST_KEY, hostname);

		ret.put(ca.ubc.ctlt.copyalerts.db.entities.Status.STATUS_CURHOST_KEY, HostResolver.getHostname());
		Gson gson = new Gson();
		return gson.toJson(ret);
	}
}
