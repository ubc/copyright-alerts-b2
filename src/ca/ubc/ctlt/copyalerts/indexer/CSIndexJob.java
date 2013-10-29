package ca.ubc.ctlt.copyalerts.indexer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

import org.quartz.DateBuilder.IntervalUnit;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.TriggerListener;
import org.quartz.UnableToInterruptJobException;
import org.quartz.jobs.NoOpJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.DateBuilder.*;
import static org.quartz.impl.matchers.GroupMatcher.*;

import blackboard.cms.filesystem.CSContext;
import blackboard.cms.filesystem.CSEntry;
import blackboard.cms.filesystem.CSFile;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.persist.PersistenceException;
import blackboard.platform.vxi.service.VirtualSystemException;

import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.db.QueueTable;

@DisallowConcurrentExecution
public class CSIndexJob implements InterruptableJob, TriggerListener
{
	private final static Logger logger = LoggerFactory.getLogger(CSIndexJob.class);

	// Execute will check this variable periodically. If true, it'll immediately stop execution.
	public Boolean stop = false;
	
	public CSIndexJob() throws ConnectionNotAvailableException
	{
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException
	{
		logger.info("Indexing Start");

		// only 1 of the servers should be running indexing, check if this is us
		String hostname = context.getJobDetail().getJobDataMap().getString("hostname");
		HostsTable ht;
		try
		{
			ht = new HostsTable();
			if (!ht.getLeader().equals(hostname))
			{
				logger.info("We're not selected as the alert generation host, stopping.");
				return;
			}
		} catch (VirtualSystemException e)
		{
			logger.error(e.getMessage(), e);
			throw new JobExecutionException(e);
		} catch (InaccessibleDbException e)
		{
			logger.error(e.getMessage(), e);
			throw new JobExecutionException(e);
		}

		// Implement execution time limit (if needed)
		// Basically, we'll have a trigger that'll fire after the time limit has passed. We use the CSIndexJob object as a trigger listener
		// and will trigger an interrupt when the time has passed.
		SavedConfiguration config;
		Timestamp started = new Timestamp((new Date()).getTime());
		try
		{
			// Save the fact that we've started running
			ht.saveRunStats(hostname, HostsTable.STATUS_RUNNING, started, new Timestamp(0));
			// load configuration
			config = SavedConfiguration.getInstance();
		} catch (InaccessibleDbException e)
		{
			logger.error(e.getMessage(), e);
			throw new JobExecutionException(e);
		}
		Scheduler sched = context.getScheduler();
		JobDetail noopJob = newJob(NoOpJob.class).withIdentity("CSIndexJobNoOp", "CSIndexJobGroup").build();
		Trigger trigger;
		if (config.isLimited())
		{
			int minutes = (config.getHours() * 60) + config.getMinutes();
			logger.info("Limit execution to " + minutes + " minutes.");
			trigger = newTrigger()
					.withIdentity("CSIndexJobStopTrigger", "CSIndexJobGroup")
					.startAt(futureDate(minutes, IntervalUnit.MINUTE))
					.build();
			try
			{
				sched.scheduleJob(noopJob, trigger);
				sched.getListenerManager().addTriggerListener(this, triggerGroupEquals("CSIndexJobGroup"));
			} catch (SchedulerException e)
			{
				logger.error("Unable to schedule job or add limit listener.");
				throw new JobExecutionException(e);
			}
		}
		
		// run indexing
		boolean limitReached = false;
		try
		{
			boolean ret = indexer(config);
			if (ret)
			{
				Timestamp ended = new Timestamp((new Date()).getTime());
				logger.debug("Finished by time limit");
				ht.saveRunStats(hostname, HostsTable.STATUS_LIMIT, started, ended);
				limitReached = true;
			}
		} catch (JobExecutionException e)
		{
			logger.error("Indexer failed during execution.", e);
			Timestamp ended = new Timestamp((new Date()).getTime());
			try
			{
				ht.saveRunStats(hostname, HostsTable.STATUS_ERROR, started, ended);
			} catch (InaccessibleDbException e1)
			{
				logger.error("Catastrophic error, unable to even save error notification.", e1);
			}
			throw e;
		} catch (InaccessibleDbException e)
		{
			logger.error("Indexer could not access database.", e);
			throw new JobExecutionException(e);
		} catch(Exception e)
		{
			logger.error("Indexer threw unknown exception.", e);
		}
		
		// Remove execution time limit now that we're done
		if (config.isLimited())
		{
			try
			{
				logger.debug("Removing limit trigger");
				sched.deleteJob(noopJob.getKey());
			} catch (SchedulerException e)
			{
				logger.warn("Unable to remove limit trigger listener.", e);
			}
		}

		// Save the fact that we've finished running only if we didn't finish by time limit
		if (!limitReached)
		{
			Timestamp ended = new Timestamp((new Date()).getTime());
			try
			{
				ht.saveRunStats(hostname, HostsTable.STATUS_STOPPED, started, ended);
			} catch (InaccessibleDbException e)
			{
				logger.warn("Unable to save run stats.", e);
			}
		}
		logger.info("ubc.ctlt.copyalerts Done");
	}
	
	/**
	 * The actual indexing operation
	 * @param config
	 * @return true if stopped by time limit, false otherwise
	 * @throws JobExecutionException
	 */
	private boolean indexer(SavedConfiguration config) throws JobExecutionException
	{
		// run actual job
		// part 1, try generating the queue
		QueueTable queue;
		logger.info("Queue Generation Start");
		
		ArrayList<String> paths = new ArrayList<String>();
		try
		{
			queue = new QueueTable();

			paths = queue.load();
			if (paths.isEmpty())
			{
				QueueGenerator generator = new QueueGenerator();
				// put files into the queue, 500 at a time, making sure to check if we need to stop
				while (generator.hasNext())
				{
					paths = generator.next();
					queue.add(paths);
					if (syncStop())
					{
						return true;
					}
				}
				// now we can process the paths from the start
				paths = queue.load();
			}
		} catch (InaccessibleDbException e1)
		{
			logger.error("Could not access database, stopping index job.", e1);
			throw new JobExecutionException(e1);
		} catch (PersistenceException e)
		{
			logger.error("Persistence Exception.", e);
			throw new JobExecutionException(e);
		}
		// make sure not to execute next part if we're supposed to halt
		logger.info("Queue Generation Done");
		if (syncStop())
		{
			return true;
		}
		// part 2, go through the queue and check each file's metadata
		logger.info("Check Metadata Start");
		IndexGenerator indexGen;
		try
		{
			indexGen = new IndexGenerator(config.getAttributes());
		} catch (PersistenceException e)
		{
			logger.error("Could not get metadata template attributes.", e);
			throw new JobExecutionException(e);
		}
		// clear the database
		try
		{
			FilesTable ft = new FilesTable();
			ft.deleteAll();
		} catch (InaccessibleDbException e)
		{
			logger.error("Could not reset the database.", e);
			throw new JobExecutionException(e);
		}
		while (!paths.isEmpty())
		{
			logger.debug("Copyright Alerts Indexing: " + paths.get(0));
			for (String p : paths)
			{
				CSContext ctx = CSContext.getContext();
				// Give ourself permission to do anything in the Content Collections.
				// Must do this cause we don't have a real request contest that many of the CS API calls
				// require when you're not a superuser.
				ctx.isSuperUser(true);
				// Retrieve file entry
				CSEntry entry = ctx.findEntry(p);
				if (entry == null)
				{
					continue;
				}
				CSFile file = (CSFile) entry;
				// Retrieve metadata
				try
				{
					indexGen.process(file);
				} catch (PersistenceException e)
				{
					logger.error("Could not access BB database, stopping index job.", e);
					throw new JobExecutionException(e);
				} catch (InaccessibleDbException e)
				{
					logger.error("Could not access database, stopping index job.", e);
					throw new JobExecutionException(e);
				}
			}

			// load next batch of files
			try
			{
				queue.pop();
				paths = queue.load();
			} catch (InaccessibleDbException e)
			{
				logger.error("Could not access database, stopping index job.", e);
				throw new JobExecutionException(e);
			}
			if (syncStop())
			{
				return true;
			}
		}
		logger.info("Check Metadata Done");
		return false;
	}
	
	@Override
	public void interrupt() throws UnableToInterruptJobException
	{
		logger.debug("Index job interrupt.");

		// inform execute that it should stop now
		synchronized (stop)
		{
			stop = true;
		}
	}

	/**
	 * For implementing execution time limits, interrupt myself when time is up.
	 */
	@Override
	public void triggerFired(Trigger arg0, JobExecutionContext arg1)
	{
		try
		{
			interrupt();
		} catch (UnableToInterruptJobException e)
		{
			logger.error("Unable to self interrupt.", e);
		}
		
	}
	
	private boolean syncStop()
	{
		synchronized (stop)
		{
			if (stop)
			{
				logger.info("Indexing stopping");
				return true;
			}
		}
		return false;
	}

	// trigger required methods that I don't care about
	@Override
	public String getName()
	{
		// TODO Auto-generated method stub
		return "CSIndexJobListener";
	}
	@Override
	public void triggerComplete(Trigger arg0, JobExecutionContext arg1, CompletedExecutionInstruction arg2)
	{
	}
	@Override
	public void triggerMisfired(Trigger arg0)
	{
	}
	@Override
	public boolean vetoJobExecution(Trigger arg0, JobExecutionContext arg1)
	{
		return false;
	}

}
