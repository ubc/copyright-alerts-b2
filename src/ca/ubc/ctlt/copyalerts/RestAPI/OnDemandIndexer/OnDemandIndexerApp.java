package ca.ubc.ctlt.copyalerts.RestAPI.OnDemandIndexer;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import ca.ubc.ctlt.copyalerts.RestAPI.OnDemandIndexer.resources.ProcessFilesResource;

public class OnDemandIndexerApp extends Application
{
	/**
     * Creates a root Restlet that will receive all incoming calls.
     */
    @Override
    public synchronized Restlet createInboundRoot() {
        Router router = new Router(getContext());

        // Defines only one route
        router.attach("/processfiles", ProcessFilesResource.class);

        return router;
    }
}
