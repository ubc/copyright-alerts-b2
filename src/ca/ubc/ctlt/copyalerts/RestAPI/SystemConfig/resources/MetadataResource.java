package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.io.IOException;
import java.util.List;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

import blackboard.cms.metadata.CSFormManagerFactory;
import blackboard.cms.metadata.XythosMetadata;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.platform.forms.Form;

import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

public class MetadataResource extends ServerResource
{
	private SavedConfiguration config;
	
	@Override
	protected void doInit() throws ResourceException
	{
		config = SavedConfiguration.getInstance();
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
		} catch (IOException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (InaccessibleDbException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
	    // return the new config to caller
	    return getMetadata();
	}
}
