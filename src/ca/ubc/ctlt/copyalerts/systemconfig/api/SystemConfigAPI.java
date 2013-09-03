package ca.ubc.ctlt.copyalerts.systemconfig.api;

import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.UnableToInterruptJobException;
import org.quartz.impl.StdSchedulerFactory;

import com.google.gson.Gson;

import blackboard.persist.PersistenceException;
import blackboard.platform.context.Context;
import blackboard.platform.context.ContextManagerFactory;
import blackboard.platform.plugin.PlugInException;
import blackboard.platform.vxi.service.VirtualSystemException;
import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.CronScheduleBuilder.*;

import ca.ubc.ctlt.copyalerts.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.systemconfig.indexer.CSIndexJob;

public class SystemConfigAPI extends HttpServlet
{

	/** Autogenerated serial */
	private static final long serialVersionUID = 1738736327585377900L;
	
	private JobDetail indexJob;
	private Trigger indexTrigger;
	private Scheduler scheduler = null;
	private SavedConfiguration config = new SavedConfiguration();
	private HostsTable hostTable;
	private String hostname;

	/** 
	 * Convenience method that can be overridden to do stuff when this servlet gets placed into service.
	 */
	@Override
	public void init() throws ServletException
	{
		try
		{
			config.load();
			hostTable = new HostsTable();
			updateHost();
			updateScheduler();
		} catch (SchedulerException e)
		{
			throw new ServletException(e);
		} catch (PlugInException e)
		{
			throw new ServletException(e);
		} catch (IOException e)
		{
			throw new ServletException(e);
		} catch (InaccessibleDbException e)
		{
			throw new ServletException(e);
		}
	}

	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#destroy()
	 */
	@Override
	public void destroy()
	{
		try
		{
			// first, make sure that running jobs know to stop
			scheduler.interrupt(indexJob.getKey());
			// then shut down the scheduler
			System.out.println("Shutting down scheduler");
			scheduler.shutdown(true);

			// Even though scheduler waits for threads to end, it still needs an additional second or so
			// before it completely unloads itself
			try
			{
				Thread.sleep(1000);
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (SchedulerException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		String path = request.getPathInfo();
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		if (path.equals("/schedule"))
		{ // returns a json map of all the schedule configuration values
			response.getWriter().write(config.toJson());
		}
		else if (path.equals("/status/status"))
		{ // return a json map of the current execution status
			try
			{
				response.getWriter().write(hostTable.toStatusJson());
			} catch (VirtualSystemException e)
			{
				throw new ServletException(e);
			} catch (InaccessibleDbException e)
			{
				throw new ServletException(e);
			}
		}
		else if (path.equals("/host"))
		{
			try
			{
				hostTable.load();
			} catch (InaccessibleDbException e)
			{
				System.out.println("Unable to load hosts.");
				throw new ServletException(e);
			}
			response.getWriter().write(hostTable.toOptionsJson());
		}
		else if (path.equals("/metadata"))
		{
			response.getWriter().write(config.toJsonAttributes());
		}
		else if (path.equals("/status/stop"))
		{ // tell the currently executing job to stop
			try
			{
				scheduler.interrupt(indexJob.getKey());
			} catch (UnableToInterruptJobException e)
			{
				throw new ServletException(e);
			}
		}
		else
		{
			response.sendError(404);
		}
	}

	/**
	 * Save the new configuration settings
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		StringBuilder sb = new StringBuilder();
	    BufferedReader br = request.getReader();
	    String str = "";
	    while( (str = br.readLine()) != null ){
	        sb.append(str);
	    } 
		    
		if (request.getPathInfo().equals("/schedule"))
		{
			// read json response body, have to read this manually cause httpservlet doesn't support json, heh
		    try
			{
			    // parse the json string and save the new config
			    config.fromJson(sb.toString());
				updateScheduler();
			} catch (SchedulerException e)
			{
				System.out.println("Unable to update scheduler.");
				response.sendError(500);
				return;
			} catch (PlugInException e)
			{
				System.out.println("Unable to update configuration.");
				response.sendError(500);
				return;
			}
		    // return the new config to caller
		    doGet(request, response);
		}
		else if (request.getPathInfo().equals("/host"))
		{
		    // parse the json string and save the new host leader
	    	Gson gson = new Gson();
	    	HostOptions host = gson.fromJson(sb.toString(), HostOptions.class);
	    	try
			{
				hostTable.setLeader(host.leader);
			} catch (InaccessibleDbException e)
			{
				// TODO Auto-generated catch block
				System.out.println("Unable to access the database while writing host configuration.");
				response.sendError(500);
				return;
			}
		    // return the new config to caller
		    doGet(request, response);
		}
		else if (request.getPathInfo().equals("/metadata"))
		{
		    try
			{
				config.fromJsonAttributes(sb.toString());
			} catch (PlugInException e)
			{
				System.out.println("Unable to update configuration.");
				response.sendError(500);
				return;
			}
		    // return the new config to caller
		    doGet(request, response);
		}
		else
		{
			response.sendError(404, "Unrecognized API Call");
		}
	}
	
	/**
	 * Create or disable the scheduler as needed according to configuration.
	 * 
	 * Exceptions are just going to be tossed back up the stack. I'm not exactly sure what would cause exceptions, so hopefully we don't get any, lol.
	 * The server will just return 500s on exception.
	 * 
	 * Need a scheduler that can
	 * - confine execution to a time period
	 * - can resume execution after that time period
	 * - can check to see if it's being executed in another thread somehow and won't try to run if it finds another version of itself running
	 * - Original plan calls for leader elections to decide which node to run indexing, but manual configuration by admins with default should suffice. 
	 * @throws SchedulerException 
	 */
	private void updateScheduler() throws SchedulerException
	{
		// create and configure scheduler according to configuration
		// 1. we have no prior scheduler enabled, need to create it
		// 2. we already have a prior scheduler, need to modify its settings
		if (scheduler == null)
		{ // need to create new scheduler
			scheduler = StdSchedulerFactory.getDefaultScheduler();
			// create a job
			indexJob = newJob(CSIndexJob.class)
					.withIdentity("myJob", "group1")
					.build();
			// pass data to the job
			indexJob.getJobDataMap().put("hostname", hostname);
			// create a trigger that runs on the cron configuration
			indexTrigger = newTrigger()
			        .withIdentity("trigger1", "group1")
			        .withSchedule(cronSchedule(config.getQuartzCron()))
			        .build();
			
			// combine job and trigger and run it
			scheduler.scheduleJob(indexJob, indexTrigger);
		}
		else 
		{ // need to modify existing scheduler settings to the new settings
			Trigger trigger = newTrigger()
			        .withIdentity("trigger1", "group1")
			        .withSchedule(cronSchedule(config.getQuartzCron()))
			        .build();
			scheduler.rescheduleJob(indexTrigger.getKey(), trigger);
			indexTrigger = trigger;
		}

		if (config.isEnable() && scheduler.isInStandbyMode())
		{ // allow trigger firing if needed
			System.out.println("Start scheduling");
			scheduler.start();
		}
		else if (!config.isEnable() && scheduler.isStarted())
		{ // stop trigger firing if needed
			System.out.println("Pause scheduling");
			scheduler.standby();
		}
		
	}
	
	/**
	 * Add this host into the database.
	 * @throws ServletException
	 */
	private void updateHost() throws ServletException
	{
		try
		{
			Context ctx = ContextManagerFactory.getInstance().getContext();
			hostname = ctx.getVirtualHost().getHostname();

			if (!hostTable.contains(hostname))
			{
				hostTable.add(hostname);
			}
		} catch (VirtualSystemException e)
		{
			throw new ServletException(e);
		} catch (PersistenceException e)
		{
			throw new ServletException(e);
		} catch (InaccessibleDbException e)
		{
			throw new ServletException(e);
		}
	}
	
}