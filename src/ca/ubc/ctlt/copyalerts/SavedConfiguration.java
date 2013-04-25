package ca.ubc.ctlt.copyalerts;

import java.io.IOException;
import java.util.Properties;

import com.google.gson.Gson;

import blackboard.platform.plugin.PlugInException;

public class SavedConfiguration
{
	// Cron scheduler configuration, time to start executing
	public static String ENABLE_CONFIG = "enable"; // whether the scheduler is enabled
	public static String CRON_CONFIG = "cron"; // when to start the alert generation
	public static String LIMIT_CONFIG = "limit"; // whether we should limit how much time alerts generation gets to run
	public static String HOURS_CONFIG = "hours"; // the limiting hours
	public static String MINUTES_CONFIG = "minutes"; // the limiting minutes
	
	// private fields that will not be serialized since they're not used on the client side
	private transient Properties prop = new Properties();
	private transient Gson gson = new Gson();

	// NOTE: All configuration settings stored in Properties must be strings, so we can't just ask GSON to
	// Serialise the Properties object, since angularjs will be expecting difference datatypes for certain
	// form elements (e.g.: boolean for checkboxes), so we'll serialise the SavedConfiguration class since
	// we can specify datatypes here.

	// private fields that will be serialised
	private String enable = "false";
	private String cron = "0 1 * * 6";
	private boolean limit = false;
	private int hours = 1;
	private int minutes = 0;
	
	public SavedConfiguration()
	{
		load();
	}
	
	/**
	 * Load config settings from the configuration file.
	 */
	private void load()
	{
		try
		{
			prop = BuildingBlockHelper.loadBuildingBlockSettings();
		} catch (PlugInException e)
		{
			System.out.println("CopyrightAlert unable to find Building Block configuration file, attempting to create.");
			e.printStackTrace();
		} catch (IOException e)
		{
			System.out.println("CopyrightAlert unable to open Building Block configuration, aborting.");
			e.printStackTrace();
		}
		
		if (prop.getProperty(ENABLE_CONFIG) == null)
		{ // no prior configuration saved, establish defaults first
			prop.setProperty(ENABLE_CONFIG, enable);
			prop.setProperty(CRON_CONFIG, cron);
			prop.setProperty(LIMIT_CONFIG, Boolean.toString(limit));
			prop.setProperty(HOURS_CONFIG, Integer.toString(hours));
			prop.setProperty(MINUTES_CONFIG, Integer.toString(minutes));
			save();
		}
		else
		{ // load prior configuration
			enable = prop.getProperty(ENABLE_CONFIG);
			enable = prop.getProperty(ENABLE_CONFIG);
			cron = prop.getProperty(CRON_CONFIG);
			limit = Boolean.parseBoolean(prop.getProperty(LIMIT_CONFIG));
			hours = Integer.parseInt(prop.getProperty(HOURS_CONFIG));
			minutes = Integer.parseInt(prop.getProperty(MINUTES_CONFIG));
		}
	}
	
	/**
	 * Save config settings to the configuration file.
	 */
	private void save()
	{
		try
		{
			BuildingBlockHelper.saveBuildingBlockSettings(prop);
		} catch (PlugInException e)
		{
			System.out.println("CopyrightAlert unable to save Building Block configuration file, aborting.");
			e.printStackTrace();
		} catch (IOException e)
		{
			System.out.println("CopyrightAlert unable to open Building Block configuration, aborting.");
			e.printStackTrace();
		}
	}
	
	/**
	 * Convert the configuration values into a json string
	 * @return
	 */
	public String toJson()
	{
		return gson.toJson(this);
	}
	
	/**
	 * Parse and store configuration values from a json string
	 * @param json
	 */
	public void fromJson(String json)
	{
		prop = gson.fromJson(json, prop.getClass());
		save();
		load(); // need to reload the new values
	}

	/**
	 * Indicates whether the scheduler is enabled.
	 * @return the enable
	 */
	public boolean getEnable()
	{
		if (enable.equals("true")) return true;
		return false;
	}

	/**
	 * Indicates when the alert generation should start running
	 * @return the cron
	 */
	public String getCron()
	{
		return cron;
	}

	/**
	 * Indicates whether there is a limit to how long alerts can run for
	 * @return the limit
	 */
	public boolean getLimit()
	{
		return limit;
	}

	/**
	 * The number of hours to limit alert generation to.
	 * @return the hours
	 */
	public int getHours()
	{
		return hours;
	}

	/**
	 * In addition to hours, the number of minutes to limit alert generation to.
	 * @return the minutes
	 */
	public int getMinutes()
	{
		return minutes;
	}

}
