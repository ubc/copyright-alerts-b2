package ca.ubc.ctlt.copyalerts.JsonIntermediate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

// quick convenience class to handle json conversion
public class HostOptions
{
	public String leader = "";
	public Collection<String> options = new ArrayList<String>();
	public Map<String, String> alt = null;
}
