package ca.ubc.ctlt.copyalerts.indexer;

import java.io.IOException;
import java.util.ArrayList;

import org.quartz.DateBuilder.IntervalUnit;
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

import static org.quartz.JobBuilder.*;
import static org.quartz.TriggerBuilder.*;
import static org.quartz.DateBuilder.*;
import static org.quartz.impl.matchers.GroupMatcher.*;

import blackboard.cms.filesystem.CSAccessControlEntry;
import blackboard.cms.filesystem.CSContext;
import blackboard.cms.filesystem.CSEntry;
import blackboard.cms.filesystem.CSEntryMetadata;
import blackboard.cms.filesystem.CSFile;
import blackboard.data.user.User;
import blackboard.db.ConnectionNotAvailableException;
import blackboard.persist.PersistenceException;
import blackboard.platform.plugin.PlugInException;

import ca.ubc.ctlt.copyalerts.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.db.QueueTable;

public class CSIndexJob implements InterruptableJob, TriggerListener
{
	// Meant to keep two threads from running indexing at the same time, need to be static as it's shared between all instances
	public static Boolean executing = false; // an intrinsic lock using Java's synchronized statement
	
	// Execute will check this variable periodically. If true, it'll immediately stop execution.
	public Boolean stop = false;
	
	// interrupt() will not return until it's sure execute got the stop message. We indicate that execute has received the stop message with this.
	public Boolean interruptProcessed = false;
	
	public CSIndexJob() throws ConnectionNotAvailableException
	{
	}

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException
	{
		String id = context.getFireTime().toString();
		System.out.println(id + " Start");

		// only one thread should ever be indexing at the same time
		synchronized(executing)
		{
			if (executing)
			{
				System.out.println(id + " stopping, already executing.");
				return;
			}
			else
			{
				System.out.println(id + " proceeding, no previous execution.");
				executing = true;
			}
		}
		
		
		// Implement execution time limit (if needed)
		// Basically, we'll have a trigger that'll fire after the time limit has passed. We use the CSIndexJob object as a trigger listener
		// and will trigger an interrupt when the time has passed.
		SavedConfiguration config = new SavedConfiguration();
		try
		{
			config.load();
		} catch (PlugInException e)
		{
			throw new JobExecutionException(e);
		} catch (IOException e)
		{
			throw new JobExecutionException(e);
		}
		Scheduler sched = context.getScheduler();
		JobDetail noopJob = newJob(NoOpJob.class).withIdentity("CSIndexJobNoOp", "CSIndexJobGroup").build();
		Trigger trigger;
		if (config.isLimited())
		{
			System.out.println("Limit enabled");
			int minutes = (config.getHours() * 60) + config.getMinutes();
			System.out.println("Limit at: " + minutes);
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
				System.out.println("Unable to schedule job or add limit listener.");
				throw new JobExecutionException(e);
			}
		}

		// run actual job
		// part 1, try generating the queue
		QueueTable queue;
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
						break;
					}
				}
				// now we can process the paths from the start
				paths = queue.load();
			}
		} catch (InaccessibleDbException e1)
		{
			System.out.println("Could not access database, stopping index job.");
			throw new JobExecutionException(e1);
		} catch (PersistenceException e)
		{
			System.out.println("Persistence Exception.");
			throw new JobExecutionException(e);
		}
		// make sure not to execute next part if we're supposed to halt
		if (syncStop())
		{
			paths.clear();
		}
		// part 2, go through the queue and check each file's metadata
		IndexGenerator indexGen = new IndexGenerator(config.getAttributes());
		while (!paths.isEmpty())
		{
			for (String p : paths)
			{
				System.out.println("Path: " + p);
				// have to provide a fake user or getContext is not happy
				User user = new User();
				CSContext ctx = CSContext.getContext(user);
				// Give ourself permission to do anything in the Content Collections.
				// Must do this cause we don't have a real request contest that many of the CS API calls
				// require when you're not a superuser.
				ctx.isSuperUser(true);
				// Retrieve file entry
				CSEntry entry = ctx.findEntry(p);
				CSFile file = (CSFile) entry;
				// Retrieve metadata
				indexGen.process(file);
			}
			try
			{
				Thread.sleep(500);
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try
			{
				queue.pop();
				paths = queue.load();
			} catch (InaccessibleDbException e)
			{
				System.out.println("Could not access database, stopping index job.");
				throw new JobExecutionException(e);
			}
			if (syncStop())
			{
				break;
			}
		}
		
		// Remove execution time limit now that we're done
		if (config.isLimited())
		{
			try
			{
				System.out.println("Removing limit trigger");
				sched.deleteJob(noopJob.getKey());
				//sched.getListenerManager().removeTriggerListener(getName());
			} catch (SchedulerException e)
			{
				System.out.println("Unable to remove limit trigger listener.");
				e.printStackTrace();
			}
		}
		

		System.out.println(id + " Fire!");
		
		// let others execute now
		synchronized (executing)
		{
			executing = false;
		}

	}

	@Override
	public void interrupt() throws UnableToInterruptJobException
	{
		System.out.println("In interrupt");

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
			System.out.println("Unable to self interrupt.");
			e.printStackTrace();
		}
		
	}
	
	private boolean syncStop()
	{
		synchronized (stop)
		{
			if (stop)
			{
				System.out.println("Execute stopping");
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
