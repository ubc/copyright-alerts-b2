package ca.ubc.ctlt.copyalerts.RestAPI.AlertsModule;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import ca.ubc.ctlt.copyalerts.RestAPI.AuthRouter;
import ca.ubc.ctlt.copyalerts.RestAPI.AlertsModule.resources.FileListResource;

public class AlertsModuleApp extends Application
{
    /**
     * Creates a root Restlet that will receive all incoming calls.
     */
    @Override
    public synchronized Restlet createInboundRoot() {
        // Create a router Restlet that routes each call to a new instance of HelloWorldResource.
        Router router = new AuthRouter(getContext());

        // Defines only one route
        router.attach("/files", FileListResource.class);
        router.attach("/files/{course}/{page}", FileListResource.class);

        return router;
    }
}
