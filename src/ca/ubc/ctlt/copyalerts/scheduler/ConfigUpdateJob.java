package ca.ubc.ctlt.copyalerts.scheduler;

import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
		} catch (IOException e)
		{
			logger.error("Unable to update scheduler, io error.", e);
		}

	}

}
