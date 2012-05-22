package org.openengsb.loom.java;

import org.openengsb.core.api.model.ConnectorConfiguration;
import org.openengsb.core.api.model.ConnectorDescription;

public interface ProtocolHandler {

    ConnectorConfiguration registerRequestHandler(LocalRequestHandler requestHandler, ConnectorDescription description);

    RequestHandler createOutgoingRequestHandler();

    void destroy();

}
