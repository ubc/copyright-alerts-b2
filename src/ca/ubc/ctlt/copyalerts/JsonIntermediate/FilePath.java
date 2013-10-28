package ca.ubc.ctlt.copyalerts.JsonIntermediate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FilePath
{
	private final static Logger logger = LoggerFactory.getLogger(FilePath.class);
	
	public String name;
	public String encodedPath;
	public String rawPath;

	public FilePath(String path)
	{
		this.rawPath = path;
		// make sure to urlencode the path as we're going to use it as a parameter to the copyright metadata building block
		try
		{
			this.encodedPath = URLEncoder.encode(path, "utf-8");
		} catch (UnsupportedEncodingException e)
		{
			logger.error(e.getMessage(), e);
		}
		// parse out the file name from the path
		name = path.substring(path.lastIndexOf("/") + 1);
	}
}
