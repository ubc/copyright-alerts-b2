package ca.ubc.ctlt.copyalerts.JsonIntermediate;

public class ScheduleConfiguration
{
	public String enable;
	public String cron;
	public boolean limit;
	public int hours;
	public int minutes;
	public SyncStatus syncstatus;
	
	public ScheduleConfiguration()
	{
		reset();
	}
	
	public void reset()
	{
		enable = "false";
		cron = "0 1 * * 6";
		limit = false;
		hours = 1;
		minutes = 0;
		syncstatus = new SyncStatus();
	}
}
