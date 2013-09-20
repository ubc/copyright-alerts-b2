package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.router;

import org.restlet.Application;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.api.HostAPI;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.api.MetadataAPI;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.api.ScheduleAPI;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.api.StatusAPI;

public class SystemConfigApp extends Application
{
	/**
     * Creates a root Restlet that will receive all incoming calls.
     */
    @Override
    public synchronized Restlet createInboundRoot() {
        Router router = new Router(getContext());

        // Defines only one route
        router.attach("/status/{action}", StatusAPI.class);
        router.attach("/metadata", MetadataAPI.class);
        router.attach("/host", HostAPI.class);
        router.attach("/schedule", ScheduleAPI.class);

        return router;
    }
}
