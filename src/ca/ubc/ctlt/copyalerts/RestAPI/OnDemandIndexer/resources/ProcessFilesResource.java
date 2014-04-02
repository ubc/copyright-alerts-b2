package ca.ubc.ctlt.copyalerts.RestAPI.OnDemandIndexer.resources;

import java.io.IOException;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.ubc.ctlt.copyalerts.JsonIntermediate.ProcessFiles;
import ca.ubc.ctlt.copyalerts.configuration.SavedConfiguration;
import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.indexer.CSIndexJob;
import ca.ubc.ctlt.copyalerts.indexer.IndexGenerator;

import com.google.gson.Gson;

import blackboard.cms.filesystem.CSContext;
import blackboard.cms.filesystem.CSEntry;
import blackboard.cms.filesystem.CSFile;
import blackboard.persist.PersistenceException;

public class ProcessFilesResource extends ServerResource
{
	private final static Logger logger = LoggerFactory.getLogger(ProcessFilesResource.class);
	
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
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}

		Gson gson = new Gson();
		ProcessFiles pf = gson.fromJson(json, ProcessFiles.class);

		IndexGenerator gen;
		try
		{
			gen = new IndexGenerator(config.getAttributes());
		} catch (PersistenceException e)
		{
			logger.error(e.getMessage(), e);
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
			return null;
		}
		FilesTable ft = new FilesTable();

		if (pf.files.size() > CSIndexJob.BATCHSIZE) return null; // only allow real time updates if they're not too large

		for (String file : pf.files)
		{
			if (file.contains("xid-")) continue; // make sure to skip special xid files
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
						logger.debug("OnDemandIndexer - File removed " + file);
					} catch (InaccessibleDbException e)
					{
						logger.error(e.getMessage(), e);
						getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
						return null;
					}
				}
			}
		}

		return null;
	}

}
