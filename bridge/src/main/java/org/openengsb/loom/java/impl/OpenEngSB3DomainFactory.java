/**
 * Licensed to the Austrian Association for Software Tool Integration (AASTI)
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. The AASTI licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openengsb.loom.java.impl;

import java.lang.reflect.Proxy;
import java.util.HashMap;

import javax.jms.JMSException;

import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.Domain;
import org.openengsb.core.api.model.ConnectorConfiguration;
import org.openengsb.core.api.model.ConnectorDescription;

public class OpenEngSB3DomainFactory {

    private ProtocolHandler remoteConfig;

    public OpenEngSB3DomainFactory(ProtocolHandler remoteConfig) throws JMSException {
        this.remoteConfig = remoteConfig;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRemoteProxy(Class<T> serviceType, final String serviceId) {
        RequestHandler requestHandler = remoteConfig.createOutgoingRequestHandler();
        ClassLoader classLoader = getClass().getClassLoader();
        Class<?>[] interfaces = new Class<?>[]{ serviceType };
        RemoteServiceHandler remoteRequestHandler = new RemoteServiceHandler(serviceId, requestHandler);
        return (T) Proxy.newProxyInstance(classLoader, interfaces, remoteRequestHandler);
    }

    public String createConnector(String domainType, Domain connectorInstance)
        throws ConnectorValidationFailedException, JMSException {

        LocalRequestHandler remoteRequestHandler = new LocalRequestHandler(connectorInstance);
        ConnectorDescription connectorDescription = new ConnectorDescription(
            domainType, "external-connector-proxy",
            new HashMap<String, String>(), new HashMap<String, Object>());
        ConnectorConfiguration config = remoteConfig.registerRequestHandler(remoteRequestHandler,
            connectorDescription);
        ConnectorManager cm = getConnectorManager();
        cm.createWithId(config.getConnectorId(), config.getContent());
        return config.getConnectorId();
    }

    public void deleteConnector(String id) {
        ConnectorManager connectorManager = getConnectorManager();
        connectorManager.delete(id);
    }

    private ConnectorManager getConnectorManager() {
        return getRemoteProxy(ConnectorManager.class, null);
    }

}
