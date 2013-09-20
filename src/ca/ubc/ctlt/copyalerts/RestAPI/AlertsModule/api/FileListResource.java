package ca.ubc.ctlt.copyalerts.RestAPI.AlertsModule.api;

import org.restlet.data.Status;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import com.google.gson.Gson;

import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;
import ca.ubc.ctlt.copyalerts.jsonintermediate.CourseFiles;
import ca.ubc.ctlt.copyalerts.jsonintermediate.FileList;

import blackboard.data.user.User;
import blackboard.platform.context.Context;
import blackboard.platform.context.ContextManagerFactory;

public class FileListResource extends ServerResource
{
	/** 
	 * The resulting json should look something like this:
	{
		"courses": 
		[
		 	{
		 		"name": "Test100",
		 		"files": [{"path": "/whatever/stuff", "name": "stuff"}, {"path": "/whatever/stuff", "name": "stuff"}],
				"page": 1,
				"numPages": 5
		 	},
		 	{
		 		"name": "Test200",
		 		"files": [{"path": "/whatever/stuff", "name": "stuff"}, {"path": "/whatever/stuff", "name": "stuff"}],
				"page": 1,
				"numPages": 5
		 	},
		],
	}
	*/
	@Get("json")
	public Representation getFiles()
	{
		// WORRY ABOUT PAGES/NUMPAGES IN COURSEFILES LATER
		// need to fix filelist resource
		Context ctx = ContextManagerFactory.getInstance().getContext();
		User user = ctx.getUser();
		FilesTable ft = new FilesTable();
		try
		{
			String userid = user.getId().toExternalString();
			
			if (this.getRequestAttributes().containsKey("course") && this.getRequestAttributes().containsKey("page"))
			{ // only want to load a specific course and page
				String course = (String) getRequestAttributes().get("course");
				int page = Integer.parseInt((String) getRequestAttributes().get("page"));
				CourseFiles ret = ft.loadCourseFiles(userid, course, page);
				Gson gson = new Gson();
				getResponse().setStatus(Status.SUCCESS_OK);
				return new JsonRepresentation(gson.toJson(ret));
			}
			else
			{ // wants all the courses
				FileList ret = ft.load(userid);
				Gson gson = new Gson();
				getResponse().setStatus(Status.SUCCESS_OK);
				return new JsonRepresentation(gson.toJson(ret));
			}
		} catch (InaccessibleDbException e)
		{
			e.printStackTrace();
			getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
		} 
		return null;
	}
}