package ca.ubc.ctlt.copyalerts.db.operations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
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

	/**
	 * Return table name based on database. E.g. for oracle, BBLEARN_cms_doc.xyf_urls, for postgres, xyf_urls
	 *
	 * @param conn Connection that query is running based on
	 * @return table name
     */
	public String getTableName(Connection conn) throws SQLException {
		DatabaseMetaData meta = conn.getMetaData();
		if (meta.getDatabaseProductName().equals("PostgreSQL")) {
			String[] name = tableName.split("\\.");
			if (name.length > 1) {
				return name[1];
			}
		}

		return tableName;
	}

	public List<String> getDataColumnNames()
	{
		return dataColumnNames;
	}

	public String getDataColumnNameString() {
		String dataColumnNames = "";
		for (String name : getDataColumnNames())
		{
			dataColumnNames += name + ", ";
		}

		return dataColumnNames;
	}

	public String getQueueQuery(Connection conn, long queueOffset) throws SQLException {
		if (queueOffset > 0) {
			// resuming from an interrupted queue generation
			return getResumeQueueQuery(conn, queueOffset);
		} else {
			return getNewQueueQuery(conn);
		}
	}

	public String getNewQueueQuery(Connection conn) throws SQLException {
		String query;
		DatabaseMetaData meta = conn.getMetaData();
		if (meta.getDatabaseProductName().equals("PostgreSQL")) {
			query = "SELECT " + getRowIdKey() + ", "+ getDataColumnNameString() + getRowIdKey() + " FROM "+
					getTableName(conn) +" ORDER BY " + getRowIdKey();
		} else {
			query = "SELECT rownum, "+ getDataColumnNameString() + getRowIdKey() + " FROM "+
					getTableName(conn) +" ORDER BY " + getRowIdKey();
		}

		return query;
	}

	public String getResumeQueueQuery(Connection conn, long queueOffset) throws SQLException {
		StringBuilder query = new StringBuilder();
		switch (conn.getMetaData().getDatabaseProductName()) {
			case "PostgreSQL":
				// Postgres and Oracle can use the same query
			case "Oracle":
				// Example query: SELECT rn, full_path, file_id FROM
				// (SELECT full_path, file_id, ROW_NUMBER() OVER (ORDER BY file_id) rn FROM BBLEARN_CMS_DOC.xyf_urls)
				// WHERE rn > 123
				query.append("SELECT rn, ").append(getDataColumnNameString()).append(getRowIdKey()).append(" ");
				query.append("FROM ");
				query.append("  (SELECT ").append(getDataColumnNameString()).append(getRowIdKey()).append(", ROW_NUMBER() OVER ");
				query.append("  (ORDER BY ").append(getRowIdKey()).append(") rn FROM ").append(getTableName(conn)).append(") a");
				query.append("WHERE rn > ").append(queueOffset);
				break;
			default:
				throw new RuntimeException("Unsupported database " + conn.getMetaData().getDatabaseProductName());
		}

		return query.toString();
	}

	public String getQueueOffsetQuery(Connection conn) throws SQLException {
		StringBuilder query = new StringBuilder();
		switch (conn.getMetaData().getDatabaseProductName()) {
			case "PostgreSQL":
			case "Oracle":
				// Example Query: SELECT * FROM (SELECT row_number() OVER (ORDER BY file_id) rn, file_id FROM bblearn_cms_doc.xyf_urls) a WHERE file_id=123
				// old oracle Example Query: SELECT * FROM (SELECT rownum, file_id FROM bblearn_cms_doc.xyf_urls ORDER BY file_id) WHERE file_id=123
				query.append("SELECT * FROM (");
				query.append("  SELECT row_number() OVER (ORDER BY ").append(getRowIdKey()).append(") rn, ").append(getRowIdKey());
				query.append("  FROM ").append(getTableName(conn)).append(") a ");
				query.append("WHERE ").append(getRowIdKey()).append(" = ?");
				break;
//				query.append("SELECT * FROM (");
//				query.append("  SELECT rownum, ").append(getRowIdKey());
//				query.append("  FROM ").append(getTableName(conn));
//				query.append("  ORDER BY ").append(getRowIdKey()).append(") ");
//				query.append("WHERE ").append(getRowIdKey()).append(" = ?");
//				break;
			default:
				throw new RuntimeException("Unsupported database " + conn.getMetaData().getDatabaseProductName());
		}

		return query.toString();
	}

}
