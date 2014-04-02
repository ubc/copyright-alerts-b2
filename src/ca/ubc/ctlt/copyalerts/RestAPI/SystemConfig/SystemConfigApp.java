package ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig;

import org.restlet.Application;
import org.restlet.Restlet;
import ca.ubc.ctlt.copyalerts.RestAPI.AuthRouter;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources.HostResource;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources.MetadataResource;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources.ResetResource;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources.ScheduleResource;
import ca.ubc.ctlt.copyalerts.RestAPI.SystemConfig.resources.StatusResource;

public class SystemConfigApp extends Application
{
	/**
     * Creates a root Restlet that will receive all incoming calls.
     */
    @Override
    public synchronized Restlet createInboundRoot() {
        AuthRouter router = new AuthRouter(getContext());
        router.setSysAdminOnly(true);

        // Defines only one route
        router.attach("/status/{action}", StatusResource.class);
        router.attach("/metadata", MetadataResource.class);
        router.attach("/host", HostResource.class);
        router.attach("/schedule", ScheduleResource.class);
        router.attach("/resetdb", ResetResource.class);

        return router;
    }
}
