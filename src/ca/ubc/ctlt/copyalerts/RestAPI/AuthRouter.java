package ca.ubc.ctlt.copyalerts.RestAPI;

import org.restlet.data.Status;
import org.restlet.resource.ServerResource;
import org.restlet.routing.Router;
import org.restlet.routing.TemplateRoute;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.routing.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import blackboard.data.user.User;
import blackboard.platform.context.Context;
import blackboard.platform.context.ContextManagerFactory;

public class AuthRouter extends Router
{
	private final static Logger logger = LoggerFactory.getLogger(AuthRouter.class);

	private class AuthFilter extends Filter
	{
		@Override
		protected int beforeHandle(Request request, Response response)
		{
			int result = STOP;
			
			Context ctx = ContextManagerFactory.getInstance().getContext();
			if (ctx.getSession().isAuthenticated())
			{
				result = CONTINUE;
			}
			else
			{
				response.setStatus(Status.CLIENT_ERROR_FORBIDDEN);
			}
			
			return result;
		}
	}
	
	private class SysAdminAuthFilter extends AuthFilter
	{

		@Override
		protected int beforeHandle(Request request, Response response)
		{
			int result = super.beforeHandle(request, response);
			
			if (result != CONTINUE) return result;

			Context ctx = ContextManagerFactory.getInstance().getContext();
			User user = ctx.getUser();
			
			if (user.getSystemRole().equals(User.SystemRole.SYSTEM_ADMIN))
			{
				return result;
			}
			response.setStatus(Status.CLIENT_ERROR_FORBIDDEN);
			return STOP;
		}
	}
	
	private boolean sysAdminOnly = false;

	public AuthRouter(org.restlet.Context context)
	{
		super(context);
	}
	
	public void setSysAdminOnly(boolean sysAdminOnly)
	{
		this.sysAdminOnly = sysAdminOnly;
	}

	// Add the authentication filter to every router
	@Override
	public TemplateRoute attach(String pathTemplate,
			Class<? extends ServerResource> targetClass)
	{
		Filter filter;
		if (sysAdminOnly)
		{
			filter = new SysAdminAuthFilter();
		}
		else
		{
			filter = new AuthFilter();
		}
		filter.setNext(targetClass);
		return super.attach(pathTemplate, filter);
	}

}
