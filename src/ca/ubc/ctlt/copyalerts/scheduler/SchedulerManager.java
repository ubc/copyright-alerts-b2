package ca.ubc.ctlt.copyalerts.scheduler;

import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.UnableToInterruptJobException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.CronScheduleBuilder.*;
import static org.quartz.SimpleScheduleBuilder.*;


import ca.ubc.ctlt.copyalerts.configuration.HostResolver;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;

public class SchedulerManager
{
	private final static Logger logger = LoggerFactory.getLogger(SchedulerManager.class);
	private static final SchedulerManager instance = new SchedulerManager();
	
	private JobDetail indexJob;
	private Trigger indexTrigger;
	private Scheduler scheduler = null;
	private SavedConfiguration config;
	
	private SchedulerManager()
	{
		try
		{
			config = SavedConfiguration.getInstance();
			updateScheduler();
		} catch (SchedulerException e)
		{
			throw new RuntimeException("Unable to start jqcron scheduler.");
		}
	}
	
	/**
	 * Make sure there is only 1 scheduler running by making this a singleton
	 * @return
	 */
	public static SchedulerManager getInstance()
	{
		return instance;
	}
	
	/**
	 * Wait for all currently executing threads to stop and then shutdown the scheduler.
	 * Must be called to cleanup the scheduler.
	 */
	public void stop()
	{
		try
		{
			// first, make sure that running jobs know to stop
			scheduler.interrupt(indexJob.getKey());
			// then shut down the scheduler
			logger.info("Shutting down scheduler");
			scheduler.shutdown(true);

			// Even though scheduler waits for threads to end, it still needs an additional second or so
			// before it completely unloads itself
			try
			{
				Thread.sleep(1000);
			} catch (InterruptedException e)
			{
				logger.warn("Unable to wait for scheduler shutdown.", e);
			}
		} catch (SchedulerException e)
		{
			logger.warn("Scheduler unable to cleanly shutdown.", e);
		}
	}
	
	/**
	 * Send an interrupt to the currently running thread to tell it to terminate as soon as possible.
	 * @throws UnableToInterruptJobException 
	 */
	public void interrupt() throws UnableToInterruptJobException
	{
		scheduler.interrupt(indexJob.getKey());
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
	public void updateScheduler() throws SchedulerException
	{
		// create and configure scheduler according to configuration
		// 1. we have no prior scheduler enabled, need to create it
		// 2. we already have a prior scheduler, need to modify its settings
		if (scheduler == null)
		{ // need to create new scheduler
			scheduler = StdSchedulerFactory.getDefaultScheduler();
			// create a job
			indexJob = newJob(CSIndexJob.class)
					.withIdentity("indexjob", "group1")
					.build();
			// pass data to the job
			indexJob.getJobDataMap().put("hostname", HostResolver.getHostname());
			// create a trigger that runs on the cron configuration
			indexTrigger = buildCronTrigger();
			
			// combine job and trigger and run it
			scheduler.scheduleJob(indexJob, indexTrigger);

			// create a job for updating configuration
			JobDetail configUpdateJob = newJob(ConfigUpdateJob.class)
					.withIdentity("configupdatejob", "configUpdateGroup")
					.build();
			// create a trigger that runs at interval to check the database for updates
			Trigger configUpdateTrigger = newTrigger()
			        .withIdentity("configupdatetrigger", "configUpdateGroup")
			        .startNow()
			        .withSchedule(simpleSchedule()
			        		.withIntervalInMinutes(60)
			        		.repeatForever())
			        .build();
			// schedule the job
			scheduler.scheduleJob(configUpdateJob, configUpdateTrigger);
			scheduler.start();
			return;
		}
		else 
		{ // need to modify existing scheduler settings to the new settings
			Trigger trigger = buildCronTrigger();
			scheduler.rescheduleJob(indexTrigger.getKey(), trigger);
			indexTrigger = trigger;
		}

		// because we recreate the trigger on every update, it loses it's previous paused status anyways,
		// so we don't need to resume the trigger, only pause it if necessary
		if (!config.isEnable())
		{ // stop index job trigger firing if needed
			logger.debug("Pause scheduling ");
			scheduler.pauseJob(indexJob.getKey());
		}
		
	}
	
	private Trigger buildCronTrigger()
	{
		Trigger trigger = newTrigger()
	        .withIdentity("trigger1", "group1")
	        .withSchedule(
	        		cronSchedule(config.getQuartzCron()).withMisfireHandlingInstructionDoNothing()
        	)
	        .build();
		return trigger;
	}
}
