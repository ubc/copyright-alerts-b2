package ca.ubc.ctlt.copyalerts.db.operations;

import java.util.List;

/**
 * Stores information necessary for scan resume. The strategy we're using is to
 * first try to locate the last item processed in the table, then pick up from
 * there. If the last item processed is not there (might've been deleted since
 * we last saw it), then we just blindly pick back up from its former location.
 * 
 */
public class ResumableScanInfo
{
	/** Name of the table */
	private String tableName;
	/** The name of the columns for which we want to retrieve data from during the scan */
	private List<String> dataColumnNames;

	// Information on the last item processed before the scan was interrupted.
	/** The name of the column used as the unique id for identifying the last item. */
	private String rowIdName;
	/** The actual value of the unique id for identifying the last item. */
	private long rowIdVal;
	/** The location (offset from the first entry in the table) at the time of the last item */
	private long rowOffset;

	public ResumableScanInfo(String tableName, List<String> dataKeys,
			String rowIdName, long rowIdVal, long rowOffset)
	{
		super();
		this.tableName = tableName;
		this.dataColumnNames = dataKeys;
		this.rowIdName = rowIdName;
		this.rowIdVal = rowIdVal;
		this.rowOffset = rowOffset;
	}

	public String getRowIdKey()
	{
		return rowIdName;
	}
	public long getRowIdVal()
	{
		return rowIdVal;
	}
	public long getRowOffset()
	{
		return rowOffset;
	}

	public String getTableName()
	{
		return tableName;
	}

	public List<String> getDataColumnNames()
	{
		return dataColumnNames;
	}
	
}
