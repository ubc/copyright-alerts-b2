package ca.ubc.ctlt.copyalerts.JsonIntermediate;

public class ScheduleConfiguration
{
	public String enable = "false";
	public String cron = "0 1 * * 6";
	public boolean limit = false;
	public int hours = 1;
	public int minutes = 0;
	public SyncStatus syncstatus = new SyncStatus();
}
