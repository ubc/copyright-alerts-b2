package ca.ubc.ctlt.copyalerts.indexer;

import blackboard.cms.filesystem.CSContext;
import blackboard.cms.filesystem.CSFile;
import blackboard.persist.PersistenceException;
import ca.ubc.ctlt.copyalerts.configuration.HostResolver;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.*;
import ca.ubc.ctlt.copyalerts.db.entities.QueueItem;
import ca.ubc.ctlt.copyalerts.db.entities.Status;
import ca.ubc.ctlt.copyalerts.db.operations.*;
import org.quartz.DateBuilder.IntervalUnit;
import org.quartz.*;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.jobs.NoOpJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.quartz.DateBuilder.futureDate;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;
import static org.quartz.impl.matchers.GroupMatcher.triggerGroupEquals;

@DisallowConcurrentExecution
public class CSIndexJob implements InterruptableJob, TriggerListener
{
	public final static int BATCHSIZE = 500;

	private final static Logger logger = LoggerFactory.getLogger(CSIndexJob.class);

	private final static String JOBGROUP = "CSIndexJobGroup";

	// Execute will check this variable periodically. If true, it'll immediately stop execution.
	private Boolean stop = false;
	private final Object stopLock = new Object();
	// this job does nothing, but we use the trigger to tell us if the job's time limit has been reached
	private JobDetail noopJob = newJob(NoOpJob.class).withIdentity("CSIndexJobNoOp", JOBGROUP).build();

	private StatusTable st = null;
	private JobExecutionContext context = null;
	private Timestamp started = new Timestamp(0);
	private Timestamp ended = new Timestamp(0);
	private String hostname = HostResolver.getHostname();

	public CSIndexJob()
	{
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException
	{
		this.context = context;

		// only 1 of the servers should be running indexing, check if this is us
		HostsTable ht = new HostsTable();
		if (!ht.isLeader(hostname))
		{
			logger.info("We're not selected as the alert generation host, stopping.");
			return;
		}

		st = new StatusTable();
		started = new Timestamp((new Date()).getTime());

		logger.info("Indexing start at " + started + " with " + st.toString());
		updateRunningStatus(Status.STATUS_RUNNING, started, ended);

		// load configuration
		SavedConfiguration config = SavedConfiguration.getInstance();

		// Implement execution time limit (if needed)
		// Basically, we'll have a trigger that will fire after the time limit has passed.
		// We use the CSIndexJob object as a trigger listener
		// and will trigger an interrupt when the time has passed.
		try {
			setupTrigger(config);
		} catch (SchedulerException e) {
			throw cleanUpOnException(e);
		}

		// run indexing
		boolean limitReached = false;
		try {
			boolean ret = indexer(config);
			if (ret) {
				ended = new Timestamp((new Date()).getTime());
				logger.debug("Finished by time limit");
				updateRunningStatus(Status.STATUS_LIMIT, started, ended);
				limitReached = true;
			}
		} catch (JobExecutionException e) {
			logger.error("Indexer failed during execution.");
			throw cleanUpOnException(e);
		} catch(Exception e) {
			logger.error("Indexer threw unexpected exception.");
			throw cleanUpOnException(e);
		}

		// Remove execution time limit now that we're done
		removeTrigger();

		// Save the fact that we've finished running only if we didn't finish by time limit
		if (!limitReached)
		{
			ended = new Timestamp((new Date()).getTime());
			updateRunningStatus(Status.STATUS_STOPPED, started, ended);
		}
		logger.info("Indexing ended at " + ended + " with " + st.toString());
	}

	private void setupTrigger(SavedConfiguration config) throws SchedulerException {
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
			sched.scheduleJob(noopJob, trigger);
			sched.getListenerManager().addTriggerListener(this, triggerGroupEquals(JOBGROUP));
		}
	}

	private void removeTrigger() {
		Scheduler sched = context.getScheduler();
		try {
			if (sched.checkExists(noopJob.getKey())) {
				logger.debug("Removing limit trigger");
				sched.deleteJob(noopJob.getKey());
			}
		} catch (SchedulerException e) {
			logger.warn("Unable to remove limit trigger listener.", e);
		}
	}

	private void updateRunningStatus(String status, Timestamp started, Timestamp ended)
	{
		// Save the fact that we've started running
		st.saveRunStats(status, started, ended);
		logger.info("Updated status to " + status);
	}

	/**
	 * Need to remove the limit trigger if we've set it and need to update status with error
	 * @param e exception
	 * @throws JobExecutionException
	 */
	private JobExecutionException cleanUpOnException(Exception e) throws JobExecutionException
	{
		// Must log first, or additional exceptions during the rest of the cleanup can hide the original exception
		logger.error(e.getMessage(), e);

		// Remove the execution time limit, if any, that were placed on indexing
		removeTrigger();

		// Update the execution status
		if (st != null) {
			st.saveRunStats(Status.STATUS_ERROR, started, ended);
		} else {
			logger.error("Exception clean up occurred before status table was read.");
		}

		return new JobExecutionException(e);
	}

	/**
	 * The actual indexing operation
	 * @param config saved configuration
	 * @return true if stopped by time limit, false otherwise
	 * @throws JobExecutionException
	 */
	private boolean indexer(SavedConfiguration config) throws JobExecutionException
	{
		try
		{
			String stage = st.getStage();
			// Stage 1: Queue Generation
			if (stage.equals(Status.STATUS_STAGE_QUEUE))
			{
				if (stageGenerateQueue())
				{ // stopped by interrupt, so we stop here
					return true;
				}
				else
				{ // no interrupts encountered and queue generation completed successfully, advance to next stage.
					st.saveStage(Status.STATUS_STAGE_NEWFILES);
					stage = Status.STATUS_STAGE_NEWFILES;
				}
			}
			// the next two stages needs an index generator
			IndexGenerator indexGen = new IndexGenerator(config.getAttributes());
			// Stage 2: Add New Files From Queue
			if (stage.equals(Status.STATUS_STAGE_NEWFILES))
			{
				if (stageAddNewFiles(indexGen))
				{ // stopped by interrupt, halt here
					return true;
				}
				else
				{ // advance to next next stage
					st.saveStage(Status.STATUS_STAGE_UPDATE);
					stage = Status.STATUS_STAGE_UPDATE;
				}
			}
			// Stage 3: Update Existing Index
			if (stage.equals(Status.STATUS_STAGE_UPDATE))
			{
				if (stageUpdateIndex(indexGen))
				{ // stopped by interrupt, halt here
					return true;
				}
				else
				{ // reset stage system back to first stage
					st.saveStage(Status.STATUS_STAGE_QUEUE);
				}
			}
		} catch (PersistenceException e) {
			logger.error("Could not persist. Stop index job", e);
			throw new JobExecutionException(e);
		}
		return false;
	}

	/**
	 * monitor the thread for errors and notify it if the job needs to stop
	 * @param thread thread to monitor
	 * @param scanner scanner running
	 * @return true if thread is interrupted, false otherwise
	 * @throws JobExecutionException
     */
	private boolean monitorThread(Thread thread, ResumableScan scanner) throws JobExecutionException {
		while (thread.isAlive())
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
					thread.interrupt();
					thread.join();
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
	 * Generate the list of files to check for indexing.
	 * Using the API to iterate through the content system turns out to be too memory consuming
	 * so let's try reading the database directly.
	 * @return if the job is ended by interruption
	 * @throws JobExecutionException
	 */
	private boolean stageGenerateQueue() throws JobExecutionException
	{
		logger.debug("Queue Generation Start");
		ScanProcessor processor = new QueueScanProcessor(st);
		// set the column names for the data that the processor wants
		List<String> dataKeys = new ArrayList<>();
		dataKeys.add("full_path");

		// load queue resume data
		long lastQueueFileid;
		long queueOffset;
		lastQueueFileid = st.getLastQueueFileid();
		queueOffset = st.getQueueOffset();
		logger.debug("Queue Resume Offset: " + queueOffset + " File ID: " + lastQueueFileid);

		// spawn the thread that scans all files to generate the queue
		ResumableScanInfo info = new ResumableScanInfo("bblearn_cms_doc.xyf_urls", dataKeys, "file_id", lastQueueFileid, queueOffset);
		ResumableScan scanner = new ResumableScan(info, processor);

		Thread genQueueThread = new Thread(scanner);
		genQueueThread.start();

		boolean interrupted = monitorThread(genQueueThread, scanner);
		if (!interrupted) {
			logger.debug("Queue Generation End");
		}

		return interrupted;
	}

	private boolean stageAddNewFiles(IndexGenerator indexGen) throws JobExecutionException
	{
		logger.info("Check Metadata Start");
		List<QueueItem> paths;
		QueueTable queue = new QueueTable();
		try
		{
			paths = queue.load();
			ArrayList<CSFile> filesBatch = new ArrayList<>();
			while (!paths.isEmpty() && !syncStop())
			{
				logger.debug("Copyright Alerts Indexing: " + paths.get(0));
				for (QueueItem p : paths)
				{
					CSContext ctx = CSContext.getContext();
					// Give ourself permission to do anything in the Content
					// Collections.
					// Must do this cause we don't have a real request context
					// that many of the CS API calls
					// require when you're not a superuser.
					ctx.isSuperUser(true);
					// Retrieve file entry
					CSFile file = indexGen.getCSFileFromPath(p.getFilePath());
					if (file == null)
						continue; // skip, not a valid file path
					filesBatch.add(file);
					// Retrieve metadata
					if (filesBatch.size() >= BATCHSIZE)
					{
						indexGen.process(filesBatch);
						filesBatch.clear();
						if (syncStop())
						{
							break;
						}
					}
				}

				// load next batch of files
				queue.pop();
				paths = queue.load();
			}
			if (!filesBatch.isEmpty())
			{
				indexGen.process(filesBatch);
				filesBatch.clear();
			}
			if (syncStop())
			{
				return true;
			}
		} catch (PersistenceException e)
		{
			logger.error("Could not save to database.", e);
			throw new JobExecutionException(e);
		} catch (InaccessibleDbException e)
		{
			logger.error("Could not read from database.", e);
			throw new JobExecutionException(e);
		}
		logger.info("Check Metadata Done");
		return false;
	}

	/**
	 * Scans existing entries in the files database, remove those that have been tagged.
	 * @return true if stopped by interrupt, false otherwise
	 * @throws JobExecutionException
	 */
	private boolean stageUpdateIndex(IndexGenerator indexGen) throws JobExecutionException
	{
		logger.debug("Starting Update Stage.");
		ScanProcessor processor = new FilesTableUpdateScanProcessor(indexGen, st);
		// set the column names for the data that the processor wants
		List<String> dataKeys = new ArrayList<>();
		dataKeys.add("filepath");

		// load queue resume data
		long lastFilesPk1;
		long filesOffset;
		lastFilesPk1 = st.getLastFilesPk1();
		filesOffset = st.getFilesOffset();
		logger.debug("Files Resume Offset: " + filesOffset + " File ID: " + lastFilesPk1);

		// spawn the thread that scans all files to generate the queue
		ResumableScanInfo info = new ResumableScanInfo(FilesTable.TABLENAME, dataKeys, "pk1", lastFilesPk1, filesOffset);
		ResumableScan scanner = new ResumableScan(info, processor);

		Thread genQueueThread = new Thread(scanner);
		genQueueThread.start();

		// monitor the thread for errors and notify it if the job needs to stop
		boolean interrupted = monitorThread(genQueueThread, scanner);
		if (!interrupted) {
			logger.debug("Update Stage End");
		}

		return interrupted;
	}

	@Override
	public void interrupt() throws UnableToInterruptJobException
	{
		logger.debug("Index job interrupt.");

		// inform execute that it should stop now
		synchronized (stopLock)
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
		synchronized (stopLock)
		{
			if (stop)
			{
				return true;
			}
		}
		return false;
	}

	// trigger required methods that I don't care about
	@Override
	public String getName()
	{
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
