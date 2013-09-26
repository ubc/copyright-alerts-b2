package ca.ubc.ctlt.copyalerts.configuration;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

import com.google.gson.Gson;

import blackboard.platform.plugin.PlugInException;

public class SavedConfiguration
{
	// singleton instance
	private static final SavedConfiguration instance = new SavedConfiguration();

	// Cron scheduler configuration, time to start executing
	public final static String ENABLE_CONFIG = "enable"; // whether the scheduler is enabled
	public final static String CRON_CONFIG = "cron"; // when to start the alert generation
	public final static String LIMIT_CONFIG = "limit"; // whether we should limit how much time alerts generation gets to run
	public final static String HOURS_CONFIG = "hours"; // the limiting hours
	public final static String MINUTES_CONFIG = "minutes"; // the limiting minutes
	public final static String ATTRIBUTES_CONFIG = "metadata_template_attribute_ids";	// key to access the stored attribute ids
	
	// cause properties are always string, we're going to have to need a delimiter for array conversion for attributes
	public final static String DELIM = "	";
	
	// private fields that will not be serialized since they're not used on the client side
	private transient Properties prop = new Properties();
	private transient Gson gson = new Gson();
	private transient ArrayList<String> attributes = new ArrayList<String>();
	private transient HostsTable hostsTable;

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
	
	private SavedConfiguration() 
	{
		try
		{
			hostsTable = new HostsTable();
			load();
		} catch (InaccessibleDbException e)
		{
			// not reading the configuration is serious enough that the building block
			// shouldn't start up
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (IOException e)
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	public static SavedConfiguration getInstance()
	{
		return instance;
	}
	
	/**
	 * Load config settings from the configuration file.
	 * @throws InaccessibleDbException 
	 * @throws PlugInException 
	 * @throws IOException 
	 */
	private void load() throws InaccessibleDbException, IOException
	{
		String configString = hostsTable.loadConfig();
		if (configString == null) configString = "";
		StringReader input = new StringReader(configString);
		prop.load(input);
		
		if (prop.getProperty(ENABLE_CONFIG) == null)
		{ // no prior configuration saved, establish defaults first
			prop.setProperty(ENABLE_CONFIG, enable);
			prop.setProperty(CRON_CONFIG, cron);
			prop.setProperty(LIMIT_CONFIG, Boolean.toString(limit));
			prop.setProperty(HOURS_CONFIG, Integer.toString(hours));
			prop.setProperty(MINUTES_CONFIG, Integer.toString(minutes));
			prop.setProperty(ATTRIBUTES_CONFIG, "");
			save();
		}
		else
		{ // load prior configuration
			enable = prop.getProperty(ENABLE_CONFIG);
			cron = prop.getProperty(CRON_CONFIG);
			limit = Boolean.parseBoolean(prop.getProperty(LIMIT_CONFIG));
			hours = Integer.parseInt(prop.getProperty(HOURS_CONFIG));
			minutes = Integer.parseInt(prop.getProperty(MINUTES_CONFIG));

			String res = prop.getProperty(ATTRIBUTES_CONFIG);
			if (!res.isEmpty())
			{
				String[] attrArr = res.split(DELIM);
				attributes.clear();
				for (String attr : attrArr)
				{
					attributes.add(attr);
				}
			}
		}
	}
	
	/**
	 * Save config settings to the configuration file.
	 * @throws InaccessibleDbException 
	 * @throws PlugInException 
	 * @throws IOException 
	 */
	private void save() throws InaccessibleDbException, IOException
	{
		String savedAttrs = "";
		// convert array into string for saving in config file
		for (String attr : attributes)
		{
			if (savedAttrs.isEmpty())
			{
				savedAttrs = attr;
			}
			else
			{
				savedAttrs += DELIM + attr;
			}
		}
		prop.setProperty(ATTRIBUTES_CONFIG, savedAttrs);
		
		StringWriter writer = new StringWriter();
		prop.store(writer, "Copyright Alerts Configuration");
		hostsTable.saveConfig(writer.toString());
		
		load();
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
	 * @throws InaccessibleDbException 
	 * @throws IOException 
	 * @throws PlugInException 
	 */
	public void fromJson(String json) throws InaccessibleDbException, IOException
	{
		prop = gson.fromJson(json, prop.getClass());
		save();
	}

	/**
	 * Convert the configuration values into a json string
	 * @return
	 */
	public String toJsonAttributes()
	{
		// need to put it in a map as ngResource doesn't like bare arrays and prefer objects
		HashMap<String, ArrayList<String>> list = new HashMap<String, ArrayList<String>>();
		list.put("attributes", attributes);
		return gson.toJson(list);
	}
	
	/**
	 * Parse and store configuration values from a json string
	 * @param json
	 * @throws InaccessibleDbException 
	 * @throws IOException 
	 * @throws PlugInException 
	 */
	public void fromJsonAttributes(String json) throws InaccessibleDbException, IOException
	{
		HashMap<String, ArrayList<String>> list = new HashMap<String, ArrayList<String>>();
		list = gson.fromJson(json, list.getClass());
		attributes = list.get("attributes");
		save();
	}
	
	/**
	 * Indicates whether the scheduler is enabled.
	 * @return the enable
	 */
	public boolean isEnable()
	{
		if (enable.equals("true")) return true;
		return false;
	}

	/**
	 * Indicates when the alert generation should start running.
	 * Need to add a seconds field for use in Quartz since jqcron doesn't specify resolution down to seconds.
	 * Also, support for specifying both a day-of-week and a day-of-month value is not complete in Quartz,
	 * the '?' character must be used in one of these fields instead of *.
	 * @return the cron
	 */
	public String getQuartzCron()
	{
		String[] parts = cron.split(" ");
		if (parts[4].equals("*"))
		{
			parts[4] = "?";
		}
		else
		{
			// jqcron is off by one in the day of week field, so add it back if needed
			int correction = Integer.parseInt(parts[4]);
			correction += 1;
			parts[4] = Integer.toString(correction);
			parts[2] = "?";
		}
		// add the seconds field
		String ret = "0";
		// java has split() but no nice way to combine it back together?!
		for (String i : parts)
		{
			ret += " " + i;
		}
		return ret;
	}

	/**
	 * Indicates whether there is a limit to how long alerts can run for
	 * @return the limit
	 */
	public boolean isLimited()
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

	/**
	 * @return the attributes
	 */
	public ArrayList<String> getAttributes()
	{
		return attributes;
	}
}
