package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SystemConfigJspRouter extends HttpServlet
{

	/** Autogenerated */
	private static final long serialVersionUID = 5721412178372979017L;

	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		//request.setAttribute("test", "hello world!");
		// pass on request to index.jsp
		RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/view/systemconfig/index.jsp");
		if (dispatcher != null) 
		{
			dispatcher.forward(request, response);
		}
	}
}
