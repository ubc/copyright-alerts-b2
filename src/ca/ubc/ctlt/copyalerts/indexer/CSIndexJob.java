package ca.ubc.ctlt.copyalerts.indexer;

import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.UnableToInterruptJobException;

public class CSIndexJob implements InterruptableJob
{
	public final static String LOCK = "lock"; // an intrinsic lock using Java's synchronized statement
	
	public static boolean executing = false; // you MUST acquire LOCK before reading or writing to this field

	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException
	{
		String id = context.getFireTime().toString();
		System.out.println(id + " Start");

		// only one thread should ever be indexing at the same time
		synchronized(LOCK)
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

		try
		{
			Thread.sleep(10000);
		} catch (InterruptedException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println(id + " Fire!");
		
		// let others execute now
		synchronized (LOCK)
		{
			executing = false;
		}

	}

	@Override
	public void interrupt() throws UnableToInterruptJobException
	{
		// TODO Auto-generated method stub
		
	}

}
