package ca.ubc.ctlt.copyalerts.alertsmodule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;

import jsonintermediate.CourseFiles;

import blackboard.data.user.User;
import blackboard.platform.context.Context;
import blackboard.platform.context.ContextManagerFactory;

import ca.ubc.ctlt.copyalerts.db.FilesTable;
import ca.ubc.ctlt.copyalerts.db.InaccessibleDbException;

public class AlertsModuleAPI extends HttpServlet
{
	/** Autogenerated serial */
	private static final long serialVersionUID = 7933482274005364346L;

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		String path = request.getPathInfo();
		System.out.println("Path Info: " + path);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		if (path.equals("/files"))
		{ // returns a json map of all the schedule configuration values
			Context ctx = ContextManagerFactory.getInstance().getContext();
			User user = ctx.getUser();
			ArrayList<CourseFiles> courseFiles = new ArrayList<CourseFiles>();
			HashMap<String, ArrayList<String>> courseToPaths = new HashMap<String, ArrayList<String>>();
			try
			{
				// sort the file paths by course
				ArrayList<String> paths = FilesTable.load(user.getId().toExternalString());
				for (String p : paths)
				{
					String courseName = getCourseName(p);
					if (courseToPaths.containsKey(courseName))
					{ // existing entry, need to modify
						courseToPaths.get(courseName).add(p);
					}
					else
					{ // no prior entry, need to create
						ArrayList<String> newPaths = new ArrayList<String>();
						newPaths.add(p);
						courseToPaths.put(courseName, newPaths);
					}
				}
				
				// put into jsonintermediate data structure for easy json conversion
				for (String course : courseToPaths.keySet())
				{
					courseFiles.add(new CourseFiles(course, courseToPaths.get(course)));
				}
				
				HashMap<String, ArrayList<CourseFiles>> courses = new HashMap<String, ArrayList<CourseFiles>>();
				courses.put("courses", courseFiles);
				Gson gson = new Gson();
				response.getWriter().write(gson.toJson(courses));
				/* the resulting json should look something like this:
				{
					"courses": 
					[
					 	{
					 		"name": "Test100",
					 		"files": [{"path": "/whatever/stuff", "name": "stuff"}, {"path": "/whatever/stuff", "name": "stuff"}] 
					 	},
					 	{
					 		"name": "Test200",
					 		"files": [{"path": "/whatever/stuff", "name": "stuff"}, {"path": "/whatever/stuff", "name": "stuff"}] 
					 	},
					]
				}
				*/
			} catch (InaccessibleDbException e)
			{
				e.printStackTrace();
				response.sendError(500);
			}
		}
		else
		{
			response.sendError(404);
		}
	}

	public String getCourseName(String path)
	{
		// try to parse out the course name from the path
		// first remove the /courses/
		String courseName = path.substring(9);
		// next segment in the path should be the course name
		courseName = courseName.substring(0, courseName.indexOf("/"));
		return courseName;
	}

}
