package ca.ubc.ctlt.copyalerts.scheduler;

import java.io.IOException;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

public class ConfigUpdateJob implements Job
{
	private final static Logger logger = LoggerFactory.getLogger(ConfigUpdateJob.class);

	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException
	{
		try
		{
			SavedConfiguration.getInstance().load();
			SchedulerManager.getInstance().updateScheduler();
		} catch (SchedulerException e)
		{
			logger.error("Unable to update scheduler, quartz didn't like it.", e);
		} catch (InaccessibleDbException e)
		{
			logger.error("Unable to update scheduler, database inaccessible.", e);
		} catch (IOException e)
		{
			logger.error("Unable to update scheduler, io error.", e);
		}
		
	}

}
