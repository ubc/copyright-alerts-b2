package ca.ubc.ctlt.copyalerts.alertsmodule.router;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import ca.ubc.ctlt.copyalerts.alertsmodule.api.FileListResource;

public class AlertsModuleApp extends Application
{
    /**
     * Creates a root Restlet that will receive all incoming calls.
     */
    @Override
    public synchronized Restlet createInboundRoot() {
        // Create a router Restlet that routes each call to a new instance of HelloWorldResource.
        Router router = new Router(getContext());

        // Defines only one route
        router.attach("/files", FileListResource.class);
        router.attach("/files/{course}/{page}", FileListResource.class);

        return router;
    }
}
