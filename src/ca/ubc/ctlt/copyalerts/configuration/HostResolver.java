package ca.ubc.ctlt.copyalerts.configuration;

public class HostResolver
{
	public static String getHostname()
	{
		// this is Linux only
		return System.getenv("HOSTNAME");
	}
}
