package ca.ubc.ctlt.copyalerts.indexer;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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

import ca.ubc.ctlt.copyalerts.configuration.HostResolver;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.HostsTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.db.QueueTable;
import ca.ubc.ctlt.copyalerts.db.operations.QueueScanProcessor;
import ca.ubc.ctlt.copyalerts.db.operations.ResumableScan;
import ca.ubc.ctlt.copyalerts.db.operations.ResumableScanInfo;
import ca.ubc.ctlt.copyalerts.db.operations.ScanProcessor;

@DisallowConcurrentExecution
public class CSIndexJob implements InterruptableJob, TriggerListener
{
	public final static int BATCHSIZE = 100;

	private final static Logger logger = LoggerFactory.getLogger(CSIndexJob.class);
	
	private final static String JOBGROUP = "CSIndexJobGroup";
	
	// Execute will check this variable periodically. If true, it'll immediately stop execution.
	public Boolean stop = false;
	// this job does nothing, but we use the trigger to tell us if the job's time limit has been reached
	private JobDetail noopJob = newJob(NoOpJob.class).withIdentity("CSIndexJobNoOp", JOBGROUP).build();
	
	// needed to be made class vars for use by cleanUpOnException
	private HostsTable ht = null;
	private JobExecutionContext context = null;
	private Timestamp started = new Timestamp(0);
	private Timestamp ended = new Timestamp(0);
	private String hostname = HostResolver.getHostname();
	
	public CSIndexJob() throws ConnectionNotAvailableException
	{
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException
	{
		logger.info("Indexing Start");
		
		this.context = context;

		// only 1 of the servers should be running indexing, check if this is us
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
			throw cleanUpOnException(e);
		} catch (InaccessibleDbException e)
		{
			throw cleanUpOnException(e);
		}

		// Implement execution time limit (if needed)
		// Basically, we'll have a trigger that'll fire after the time limit has passed. We use the CSIndexJob object as a trigger listener
		// and will trigger an interrupt when the time has passed.
		SavedConfiguration config;
		try
		{
			// Save the fact that we've started running
			updateRunningStatus(HostsTable.STATUS_RUNNING_QUEUE);
			// load configuration
			config = SavedConfiguration.getInstance();
		} catch (InaccessibleDbException e)
		{
			throw cleanUpOnException(e);
		}
		Scheduler sched = context.getScheduler();
		Trigger trigger;
		if (config.isLimited())
		{
			int minutes = (config.getHours() * 60) + config.getMinutes();
			logger.info("Limit execution to " + minutes + " minutes.");
			trigger = newTrigger()
					.withIdentity("CSIndexJobStopTrigger", JOBGROUP)
					.startAt(futureDate(minutes, IntervalUnit.MINUTE))
					.build();
			try
			{
				sched.scheduleJob(noopJob, trigger);
				sched.getListenerManager().addTriggerListener(this, triggerGroupEquals(JOBGROUP));
			} catch (SchedulerException e)
			{
				throw cleanUpOnException(e);
			}
		}
		
		// run indexing
		boolean limitReached = false;
		try
		{
			boolean ret = indexer(config);
			if (ret)
			{
				ended = new Timestamp((new Date()).getTime());
				logger.debug("Finished by time limit");
				ht.saveRunStats(hostname, HostsTable.STATUS_LIMIT, started, ended);
				limitReached = true;
			}
		} catch (JobExecutionException e)
		{
			logger.error("Indexer failed during execution.");
			throw cleanUpOnException(e);
		} catch (InaccessibleDbException e)
		{
			logger.error("Indexer could not access database.");
			throw cleanUpOnException(e);
		} catch(Exception e)
		{
			logger.error("Indexer threw unexpected exception.");
			throw cleanUpOnException(e);
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
	
	private void updateRunningStatus(String status) throws InaccessibleDbException
	{
		started = new Timestamp((new Date()).getTime());
		// Save the fact that we've started running
		ht.saveRunStats(hostname, status, started, ended);
	}
	
	/**
	 * Need to remove the limit trigger if we've set it and need to update status with error
	 * @param e
	 * @throws JobExecutionException
	 */
	private JobExecutionException cleanUpOnException(Exception e)
	{
		// Must log first, or additional exceptions during the rest of the cleanup can hide the original exception
		logger.error(e.getMessage(), e);

		// Remove the execution time limit, if any, that were placed on indexing
		Scheduler sched = context.getScheduler();
		try
		{
			if (sched.checkExists(noopJob.getKey()))
			{
				sched.deleteJob(noopJob.getKey());
			}
		} catch (SchedulerException e1)
		{
			logger.error("Exception clean up failed, unable to remove time limit trigger.");
		}

		// Update the execution status
		try
		{
			if (ht != null)
			{
				ht.saveRunStats(hostname, HostsTable.STATUS_ERROR, started, ended);
			}
			else
			{
				logger.error("Exception clean up occured before hosts table was read.");
			}
		} catch (InaccessibleDbException e1)
		{
			logger.error("Exception clean up failed, unable to update execution status.");
		}
		return new JobExecutionException(e);
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
		// we're either going to continue processing a previously generated queue or have to generate a new queue entirely.
		// assume that we're continuing processing a previously generated queue for now
		try
		{
			queue = new QueueTable();

			int lastQueueFileid = ht.getLastQueueFileid(); // should be greater than 0 if queue generation got interrupted
			paths = queue.load();
			if (paths.isEmpty() || lastQueueFileid > 0)
			{
				// load data into the queue table
				if (generateQueue())
				{
					return true;
				}
				// now we can process the paths from the start
				paths = queue.load();
				logger.debug("Generated new queue.");
			}
			else
			{
				logger.debug("Continue with previously generated queue.");
			}
		} catch (InaccessibleDbException e1)
		{
			logger.error("Could not access database, stopping index job.", e1);
			throw new JobExecutionException(e1);
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
		// update running status
		try
		{
			updateRunningStatus(HostsTable.STATUS_RUNNING_NEWFILES);
		} catch (InaccessibleDbException e)
		{
			logger.error("Could not reset the database.", e);
			throw new JobExecutionException(e);
		}
		while (!paths.isEmpty())
		{
			logger.debug("Copyright Alerts Indexing: " + paths.get(0));
			ArrayList<CSFile> filesBatch = new ArrayList<CSFile>();
			for (String p : paths)
			{
				CSContext ctx = CSContext.getContext();
				// Give ourself permission to do anything in the Content Collections.
				// Must do this cause we don't have a real request context that many of the CS API calls
				// require when you're not a superuser.
				ctx.isSuperUser(true);
				// Retrieve file entry
				CSEntry entry = ctx.findEntry(p);
				if (entry == null)
				{
					logger.info("Non-existent file: " + p);
					continue;
				}
				if (!(entry instanceof CSFile))
				{
					logger.info("Skipping directory, Path: " + p);
					continue;
				}
				CSFile file = (CSFile) entry;
				filesBatch.add(file);
				// Retrieve metadata
				try
				{
					if (filesBatch.size() >= BATCHSIZE)
					{
						indexGen.process(filesBatch);
						filesBatch.clear();
					}
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
			if (!filesBatch.isEmpty())
			{
				try
				{
					indexGen.process(filesBatch);
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
	
	/**
	 * Generate the list of files to check for indexing.
	 * Using the API to iterate through the content system turns out to be too memory consuming
	 * so let's try reading the database directly.
	 * @return
	 * @throws JobExecutionException 
	 */
	private boolean generateQueue() throws JobExecutionException
	{
		ScanProcessor processor = new QueueScanProcessor(ht);
		// set the column names for the data that the processor wants
		List<String> dataKeys = new ArrayList<String>();
		dataKeys.add("full_path");
		
		// load queue resume data
		int lastQueueFileid = 0;
		int queueOffset = 0;
		try
		{
			lastQueueFileid = ht.getLastQueueFileid();
			queueOffset = ht.getQueueOffset();
			logger.debug("Queue Resume Offset: " + queueOffset + " File ID: " + lastQueueFileid);
		} catch (InaccessibleDbException e)
		{
			logger.error("Unable to get queue resume data.", e);
			throw new JobExecutionException(e);
		}

		// spawn the thread that scans all files to generate the queue
		ResumableScanInfo info = new ResumableScanInfo("bblearn_cms_doc.xyf_urls", dataKeys, "file_id", lastQueueFileid, queueOffset);
		ResumableScan scanner = new ResumableScan(info, processor);
		
		Thread genQueueThread = new Thread(scanner);
		genQueueThread.start();
		
		// monitor the thread for errors and notify it if the job needs to stop
		while (genQueueThread.isAlive())
		{
			try
			{
				Thread.sleep(1000); // check for error every second
				if (scanner.hasError())
				{
					throw new JobExecutionException(scanner.getError());
				}
				if (syncStop())
				{ // notify the job that we need to stop
					genQueueThread.interrupt();
					genQueueThread.join();
					return true;
				}
			} catch (InterruptedException e)
			{
				logger.debug("Interrupt Exception", e);
			}
		}
		return false;
	}
	
	/**
	 * Scans existing entries in the files database, remove those that have been tagged.
	 * @return true if stopped by interrupt, false otherwise
	 * @throws JobExecutionException 
	 */
	private boolean updateIndex() throws JobExecutionException
	{
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
