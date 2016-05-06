package ca.ubc.ctlt.copyalerts.configuration;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;

import ca.ubc.ctlt.copyalerts.db.StatusTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.ScheduleConfiguration;
import ca.ubc.ctlt.copyalerts.JsonIntermediate.SyncStatus;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

import com.google.gson.Gson;

import blackboard.cms.metadata.CSFormManagerFactory;
import blackboard.persist.Id;
import blackboard.persist.PersistenceException;
import blackboard.persist.metadata.AttributeDefinition;
import blackboard.platform.forms.Form;
import blackboard.platform.plugin.PlugInException;

public class SavedConfiguration
{
	private final static Logger logger = LoggerFactory.getLogger(SavedConfiguration.class);

	// singleton instance
	private static final SavedConfiguration instance = new SavedConfiguration();

	// Cron scheduler configuration, time to start executing
	private final static String ENABLE_CONFIG = "enable"; // whether the scheduler is enabled
	private final static String CRON_CONFIG = "cron"; // when to start the alert generation
	private final static String LIMIT_CONFIG = "limit"; // whether we should limit how much time alerts generation gets to run
	private final static String HOURS_CONFIG = "hours"; // the limiting hours
	private final static String MINUTES_CONFIG = "minutes"; // the limiting minutes
	private final static String TEMPLATE_CONFIG = "metadata_template_id";	// key to access the stored attribute ids
	
	// cause properties are always string, we're going to have to need a delimiter for array conversion for attributes
	public final static String DELIM = "	";
	
	private Properties prop = new Properties();
	private Gson gson = new Gson();
	private StatusTable statusTable;

	// allows easy serialization to json for schedule configurations
	private ScheduleConfiguration config = new ScheduleConfiguration();

	
	private SavedConfiguration() 
	{
		reset();
	}
	
	public static SavedConfiguration getInstance()
	{
		return instance;
	}
	
	public void reset()
	{
		try
		{
			prop.clear();
			config.reset();
			statusTable = new StatusTable();
			load();
		} catch (InaccessibleDbException e)
		{
			// not reading the configuration is serious enough that the building block
			// shouldn't start up
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e);
		} catch (IOException e)
		{
			logger.error(e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Load config settings from the configuration file.
	 * @throws InaccessibleDbException 
	 * @throws PlugInException 
	 * @throws IOException 
	 */
	public void load() throws InaccessibleDbException, IOException
	{
		String configString = statusTable.loadConfig();
		if (configString == null) configString = "";
		StringReader input = new StringReader(configString);
		prop.load(input);
		
		if (prop.getProperty(ENABLE_CONFIG) == null)
		{ // no prior configuration saved, establish defaults first
			loadFromConfig();
			save();
		}
		else
		{ // load prior configuration
			config.enable = prop.getProperty(ENABLE_CONFIG);
			config.cron = prop.getProperty(CRON_CONFIG);
			config.limit = Boolean.parseBoolean(prop.getProperty(LIMIT_CONFIG));
			config.hours = Integer.parseInt(prop.getProperty(HOURS_CONFIG));
			config.minutes = Integer.parseInt(prop.getProperty(MINUTES_CONFIG));
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
		StringWriter writer = new StringWriter();
		prop.store(writer, "Copyright Alerts Configuration");
		statusTable.saveConfig(writer.toString());
		
		load();
	}
	
	/**
	 * Take the values from config and put it into the properties file
	 */
	private void loadFromConfig()
	{
		prop.setProperty(ENABLE_CONFIG, config.enable);
		prop.setProperty(CRON_CONFIG, config.cron);
		prop.setProperty(LIMIT_CONFIG, Boolean.toString(config.limit));
		prop.setProperty(HOURS_CONFIG, Integer.toString(config.hours));
		prop.setProperty(MINUTES_CONFIG, Integer.toString(config.minutes));
	}
	
	/**
	 * Convert the configuration values into a json string with empty syncstatus
	 * @return
	 */
	public String toJson()
	{
		config.syncstatus = new SyncStatus();
		return gson.toJson(config);
	}
	
	/**
	 * Convert the configuration values into a json string, includes a syncstatus message
	 * @return
	 */
	public String toJson(SyncStatus status)
	{
		config.syncstatus = status;
		return gson.toJson(config);
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
		config = gson.fromJson(json, config.getClass());
		loadFromConfig();
		save();
	}
	
	public String getMetadataTemplate()
	{
		return prop.getProperty(TEMPLATE_CONFIG);
	}
	
	/**
	 * Parse and store configuration values from a json string
	 * @param template metadata template
	 * @throws InaccessibleDbException 
	 * @throws IOException 
	 * @throws PlugInException 
	 */
	public void saveMetadataTemplate(String template) throws InaccessibleDbException, IOException
	{
		prop.setProperty(TEMPLATE_CONFIG, template);
		save();
	}
	
	/**
	 * Indicates whether the scheduler is enabled.
	 * @return the enable
	 */
	public boolean isEnable()
	{
		if (config.enable.equals("true")) return true;
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
		String[] parts = config.cron.split(" ");
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
		return config.limit;
	}

	/**
	 * The number of hours to limit alert generation to.
	 * @return the hours
	 */
	public int getHours()
	{
		return config.hours;
	}

	/**
	 * In addition to hours, the number of minutes to limit alert generation to.
	 * @return the minutes
	 */
	public int getMinutes()
	{
		return config.minutes;
	}

	/**
	 * @return the attributes
	 * @throws PersistenceException 
	 */
	public ArrayList<String> getAttributes() throws PersistenceException
	{
		// find form by form ID
		Id formId = Id.generateId(Form.DATA_TYPE, prop.getProperty(TEMPLATE_CONFIG));
		Form form = CSFormManagerFactory.getInstance().loadFormById(formId);

		ArrayList<String> ret = new ArrayList<String>();
		// get all attributes in the form
		Set<AttributeDefinition> adSet = form.getAttributeDefinitions();
		for (AttributeDefinition ad : adSet) 
		{
			if (ad.getValueTypeLabel().equals("Boolean"))
			{
				ret.add(ad.getName());
			}
		}
		
		return ret;
	}
}
