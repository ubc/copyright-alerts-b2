package ca.ubc.ctlt.copyalerts.JsonIntermediate;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class FilePath
{
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// parse out the file name from the path
		name = path.substring(path.lastIndexOf("/") + 1);
	}
}
