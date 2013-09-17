package ca.ubc.ctlt.copyalerts.systemconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import blackboard.persist.PersistenceException;
import blackboard.platform.context.Context;
import blackboard.platform.context.ContextManagerFactory;
import blackboard.platform.vxi.service.VirtualSystemException;

public class HostResolver
{
	public static String getHostname()
	{
		
		// this is Linux only
		return System.getenv("HOSTNAME");
	}
	
	public static Map<String, String> getAltHostnames()
	{
		HashMap<String, String> altHostnames = new HashMap<String, String>();
		try
		{
			InetAddress addr;
			addr = InetAddress.getLocalHost();
			String host = addr.getHostName();
			altHostnames.put("DNS", host);
		}
		catch (UnknownHostException ex)
		{
			ex.printStackTrace();
		}
		
		Process p;
		try
		{
			p = new ProcessBuilder("hostname").start();
			p.waitFor();
	        // read the output from the command
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        String s;
	        String out = "";
	        while ((s = stdInput.readLine()) != null) {
	        	out += s;
	        }
			altHostnames.put("hostname", out.trim());
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		
		try
		{
			p = new ProcessBuilder("uname", "-n").start();
			p.waitFor();
	        // read the output from the command
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
	        String s;
	        String out = "";
	        while ((s = stdInput.readLine()) != null) {
	        	out += s;
	        }
			altHostnames.put("uname -n", out.trim());
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		}

		altHostnames.put("System.getProperty", System.getProperty("HOSTNAME"));

		Context ctx = ContextManagerFactory.getInstance().getContext();
		try
		{
			altHostnames.put("getVirtualHost.getHostname", ctx.getVirtualHost().getHostname());
			altHostnames.put("getVirtualInstallation.getName", ctx.getVirtualInstallation().getName());
		} catch (VirtualSystemException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PersistenceException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return altHostnames;
	}
}
