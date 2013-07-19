package ca.ubc.ctlt.copyalerts.db;

public class UserFilePair
{
	private String username;
	private String path;
	/**
	 * @param username
	 * @param path
	 */
	public UserFilePair(String username, String path)
	{
		super();
		this.username = username;
		this.path = path;
	}
	/**
	 * @return the username
	 */
	public String getUsername()
	{
		return username;
	}
	/**
	 * @return the path
	 */
	public String getPath()
	{
		return path;
	}

}
