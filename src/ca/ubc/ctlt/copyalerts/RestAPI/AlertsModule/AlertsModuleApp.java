package ca.ubc.ctlt.copyalerts.RestAPI.AlertsModule;

import ca.ubc.ctlt.copyalerts.RestAPI.AlertsModule.resources.FileListResource;
import ca.ubc.ctlt.copyalerts.RestAPI.AuthRouter;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources.StatusResource;
import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

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
        router.attach("/status", StatusResource.class);

        return router;
    }
}
