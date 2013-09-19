package ca.ubc.ctlt.copyalerts.systemconfig.api;

import java.io.BufferedReader;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.quartz.SchedulerException;
import com.google.gson.Gson;

import blackboard.platform.plugin.PlugInException;
import blackboard.platform.vxi.service.VirtualSystemException;
import ca.ubc.ctlt.copyalerts.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.jsonintermediate.HostOptions;
import ca.ubc.ctlt.copyalerts.systemconfig.HostResolver;
import ca.ubc.ctlt.copyalerts.systemconfig.scheduler.SchedulerManager;

public class SystemConfigAPI extends HttpServlet
{

	/** Autogenerated serial */
	private static final long serialVersionUID = 1738736327585377900L;
	
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
			hostTable = new HostsTable();
			updateHost();
		} catch (InaccessibleDbException e)
		{
			throw new ServletException(e);
		}
		try
		{
			SchedulerManager.getInstance().updateScheduler();
		} catch (SchedulerException e)
		{
			e.printStackTrace();
			throw new ServletException(e);
		}
	}

	/* (non-Javadoc)
	 * @see javax.servlet.GenericServlet#destroy()
	 */
	@Override
	public void destroy()
	{
		SchedulerManager.getInstance().stop();
	}

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		String path = request.getPathInfo();
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		if (path.equals("/schedule"))
		{ // returns a json map of all the schedule configuration values
			try
			{
				config.load();
			} catch (PlugInException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			response.getWriter().write(config.toJson());
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
			    SchedulerManager.getInstance().updateScheduler();
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
		else
		{
			response.sendError(404, "Unrecognized API Call");
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
			hostname = HostResolver.getHostname();

			if (!hostTable.contains(hostname))
			{
				hostTable.add(hostname);
			}
		} catch (VirtualSystemException e)
		{
			throw new ServletException(e);
		} catch (InaccessibleDbException e)
		{
			throw new ServletException(e);
		}
	}
	
}
