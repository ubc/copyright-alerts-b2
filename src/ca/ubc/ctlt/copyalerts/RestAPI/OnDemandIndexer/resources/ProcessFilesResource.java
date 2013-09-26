package ca.ubc.ctlt.copyalerts.RestAPI.OnDemandIndexer.resources;

import java.io.IOException;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.ProcessFiles;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.indexer.IndexGenerator;

import com.google.gson.Gson;

import blackboard.cms.filesystem.CSContext;
import blackboard.cms.filesystem.CSEntry;
import blackboard.cms.filesystem.CSFile;
import blackboard.data.user.User;
import blackboard.platform.context.Context;
import blackboard.platform.context.ContextManagerFactory;

public class ProcessFilesResource extends ServerResource
{
	@Get("json")
	public JsonRepresentation uselessGet()
	{
		Context ctx = ContextManagerFactory.getInstance().getContext();
		User user = ctx.getUser();
		System.out.println("UID: " + user.getId().toExternalString());
		return new JsonRepresentation("hello");
	}
	
	@Post("json")
	public JsonRepresentation processFiles(JsonRepresentation data)
	{
		SavedConfiguration config;
		String json;
		CSContext csCtx;
		try
		{
			config = SavedConfiguration.getInstance();
			csCtx = CSContext.getContext();
			csCtx.isSuperUser(true);
			json = data.getText();
		} catch (IOException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}

		Gson gson = new Gson();
		ProcessFiles pf = gson.fromJson(json, ProcessFiles.class);

		IndexGenerator gen = new IndexGenerator(config.getAttributes());
		FilesTable ft = new FilesTable();
		for (String file : pf.files)
		{
			CSEntry entry = csCtx.findEntry(file);
			if (entry == null) continue; // didn't find the file
			if (entry instanceof CSFile)
			{ // only process if it's a file
				CSFile csf = (CSFile) entry;
				if (gen.fileIsTagged(csf))
				{ // the file is now tagged, so remove it from the database
					try
					{
						ft.deleteFile(file);
						System.out.println("File removed " + file);
					} catch (InaccessibleDbException e)
					{
						e.printStackTrace();
						getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
						return null;
					}
				}
			}
		}

		return null;
	}

}
