package ca.ubc.ctlt.copyalerts.jsonintermediate;

import blackboard.persist.Id;

public class UserFilePair
{
	private Id userid;
	private String path;
	/**
	 * @param username
	 * @param path
	 */
	public UserFilePair(Id username, String path)
	{
		super();
		this.userid = username;
		this.path = path;
	}
	/**
	 * @return the username
	 */
	public Id getUserId()
	{
		return userid;
	}
	/**
	 * @return the path
	 */
	public String getPath()
	{
		return path;
	}

}
