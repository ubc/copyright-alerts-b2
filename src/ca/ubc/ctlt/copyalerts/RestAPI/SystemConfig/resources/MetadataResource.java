package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import blackboard.cms.metadata.CSFormManagerFactory;
import blackboard.cms.metadata.XythosMetadata;
import blackboard.persist.KeyNotFoundException;
import blackboard.persist.PersistenceException;
import blackboard.platform.forms.Form;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.MetadataTemplates;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

public class MetadataResource extends ServerResource
{
	private final static Logger logger = LoggerFactory.getLogger(MetadataResource.class);
	
	private SavedConfiguration config;
	private HashMap<String, String> templates = new HashMap<String, String>();
	private Gson gson = new Gson();
	
	@Override
	protected void doInit() throws ResourceException
	{
		config = SavedConfiguration.getInstance();
		super.doInit();
	}

	@Get("json")
	public Representation getMetadata()
	{
		try
		{
			MetadataTemplates t = getTemplates();
			return new JsonRepresentation(gson.toJson(t));
		} catch (KeyNotFoundException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (PersistenceException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
	}
	
	@Post("json")
	public Representation saveMetadata(Representation data)
	{
		try
		{
			String json = data.getText();
			MetadataTemplates t = gson.fromJson(json, MetadataTemplates.class);
			config.saveMetadataTemplate(t.selected);
		} catch (IOException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		} catch (InaccessibleDbException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
	    // return the new config to caller
	    return getMetadata();
	}
	
	private MetadataTemplates getTemplates() throws KeyNotFoundException, PersistenceException
	{
		templates.clear();
		List<Form> forms = CSFormManagerFactory.getInstance().loadAllForms(XythosMetadata.DATA_TYPE);
		for (Form form : forms)
		{
			templates.put(form.getId().toExternalString(), form.getTitle());
		}

		MetadataTemplates ret = new MetadataTemplates();
		ret.templatesList = templates;
		ret.selected = config.getMetadataTemplate();
		return ret;
	}
}
