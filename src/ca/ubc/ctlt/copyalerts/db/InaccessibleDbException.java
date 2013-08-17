package ca.ubc.ctlt.copyalerts.db;

public class InaccessibleDbException extends Exception
{

	public InaccessibleDbException(String string, Exception e)
	{
		super(string, e);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 5482881053968186777L;

}
