package org.openengsb.loom.java.impl;

import org.openengsb.core.api.model.ConnectorConfiguration;
import org.openengsb.core.api.model.ConnectorDescription;

public interface RemoteConfig {

    ConnectorConfiguration createConnectorHandler(LocalRequestHandler remoteRequestHandler,
            ConnectorDescription connectorDescription);

    RequestHandler createRequestHandler();

}
