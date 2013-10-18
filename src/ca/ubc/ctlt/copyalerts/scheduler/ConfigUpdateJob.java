package ca.ubc.ctlt.copyalerts.scheduler;

import java.io.IOException;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

public class ConfigUpdateJob implements Job
{
	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException
	{
		try
		{
			SavedConfiguration.getInstance().load();
			SchedulerManager.getInstance().updateScheduler();
		} catch (SchedulerException e)
		{
			System.out.println("Unable to update scheduler, quartz didn't like it.");
			e.printStackTrace();
		} catch (InaccessibleDbException e)
		{
			System.out.println("Unable to update scheduler, database inaccessible.");
			e.printStackTrace();
		} catch (IOException e)
		{
			System.out.println("Unable to update scheduler, io error.");
			e.printStackTrace();
		}
		
	}

}
