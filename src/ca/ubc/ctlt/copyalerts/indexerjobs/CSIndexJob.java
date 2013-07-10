package ca.ubc.ctlt.copyalerts.indexerjobs;

import java.io.IOException;

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

import blackboard.platform.plugin.PlugInException;

import ca.ubc.ctlt.copyalerts.SavedConfiguration;

public class CSIndexJob implements InterruptableJob, TriggerListener
{
	// Meant to keep two threads from running indexing at the same time, need to be static as it's shared between all instances
	public static Boolean executing = false; // an intrinsic lock using Java's synchronized statement
	
	// Execute will check this variable periodically. If true, it'll immediately stop execution.
	public Boolean stop = false;
	
	// interrupt() will not return until it's sure execute got the stop message. We indicate that execute has received the stop message with this.
	public Boolean interruptProcessed = false;
	

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
				e.printStackTrace();
				throw new JobExecutionException(e);
			}
		}

		// run actual job
		for (int i = 0; i < 240; i++)
		{
			try
			{
				Thread.sleep(500);
			} catch (InterruptedException e)
			{
				System.out.println("Woken up while sleeping!");
			}
			synchronized (stop)
			{
				if (stop)
				{
					System.out.println("Execute stopping");
					stop = false;
					break;
				}
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
				// TODO Auto-generated catch block
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
		System.out.println("Interrupt self");
		try
		{
			interrupt();
		} catch (UnableToInterruptJobException e)
		{
			System.out.println("Unable to self interrupt.");
			e.printStackTrace();
		}
		
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
