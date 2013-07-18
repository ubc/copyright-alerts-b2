package ca.ubc.ctlt.copyalerts.indexer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import blackboard.cms.filesystem.CSContext;
import blackboard.cms.filesystem.CSDirectory;
import blackboard.cms.filesystem.CSEntry;
import blackboard.cms.filesystem.CSFile;
import blackboard.data.user.User;
import blackboard.persist.PersistenceException;
import ca.ubc.ctlt.copyalerts.db.Queue;

public class QueueGenerator
{
	private ArrayDeque<CSEntry> frontier = new ArrayDeque<CSEntry>();
	private CSContext ctx;
	
	public QueueGenerator() throws PersistenceException
	{
		// create a fake admin user so we can get a CSContext that can read everything
		// note that creating this user is necessary cause otherwise CSContext.getContext() would return null
		// because as far as this Quartz job is aware, there are no users logged in
		try
		{
			// have to provide a fake user or getContext is not happy
			User user = new User();
			ctx = CSContext.getContext(user);
			// Give ourself permission to do anything in the Content Collections.
			// Must do this cause we don't have a real request contest that many of the CS API calls
			// require when you're not a superuser.
			ctx.isSuperUser(true);
			// Get a list of files to look for metadata on
			CSEntry root = ctx.findEntry("/");
			frontier.add(root);
		} catch (Exception e)
		{
			System.out.println("Dead");
			e.printStackTrace();
		}
	}
	
	public boolean hasNext()
	{
		return !frontier.isEmpty();
	}
	
	/**
	 * Create the list of files needed to be indexed.
	 */
	
	public ArrayList<String> next()
	{
		int count = 0;
		ArrayList<String> paths = new ArrayList<String>();
		// breadth first search down the directory tree
		while (!frontier.isEmpty())
		{
			CSEntry curEntry = frontier.pop();
			if (curEntry instanceof CSFile)
			{
				paths.add(curEntry.getFullPath());
			}
			else if (curEntry instanceof CSDirectory)
			{
				CSDirectory dir = (CSDirectory) curEntry; // we know it's a directory, so cast it
				for (CSEntry e : dir.getDirectoryContents())
				{
					frontier.add(e);
				}
			}
			// limit the number of entries to return
			count++;
			if (count == Queue.LOADNUM)
			{
				break;
			}
		}
		return paths;

	}

}
