package ca.ubc.ctlt.copyalerts.systemconfig.api;

import java.io.IOException;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import blackboard.platform.plugin.PlugInException;

import ca.ubc.ctlt.copyalerts.SavedConfiguration;

public class MetadataAPI extends ServerResource
{
	private SavedConfiguration config = new SavedConfiguration();
	
	@Override
	protected void doInit() throws ResourceException
	{
		try
		{
			config.load();
		} catch (PlugInException e)
		{
			e.printStackTrace();
			throw new ResourceException(e);
		} catch (IOException e)
		{
			e.printStackTrace();
			throw new ResourceException(e);
		}
		super.doInit();
	}

	@Get("json")
	public Representation getMetadata()
	{
		return new JsonRepresentation(config.toJsonAttributes());
	}
	
	@Post("json")
	public Representation saveMetadata(Representation data)
	{
		try
		{
			String json = data.getText();
			config.fromJsonAttributes(json);
		} catch (PlugInException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (IOException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
	    // return the new config to caller
	    return getMetadata();
	}
}
