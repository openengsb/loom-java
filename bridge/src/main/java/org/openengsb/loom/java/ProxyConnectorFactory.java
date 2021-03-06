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

package org.openengsb.loom.java;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.Domain;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.remote.ProxyConnectorRegistry;
import org.openengsb.core.api.security.Credentials;

public class ProxyConnectorFactory {

    private ProtocolHandler remoteConfig;
    private String principal;
    private Credentials credentials;

    public ProxyConnectorFactory(ProtocolHandler remoteConfig, String principal, Credentials credentials) {
        this.remoteConfig = remoteConfig;
        this.principal = principal;
        this.credentials = credentials;
    }

    public <T> T getRemoteProxy(Class<T> serviceClass) {
        return getRemoteProxy(new ServiceIdentifier(serviceClass, Collections.<String, Object>emptyMap(), null));
    }
    
    public <T> T getRemoteProxy(Class<T> serviceClass, Map<String, Object> serviceProperties) {
        return getRemoteProxy(new ServiceIdentifier(serviceClass, serviceProperties, null));
    }
    
    public <T> T getRemoteProxy(Class<T> serviceClass, final String serviceId) {
        return getRemoteProxy(new ServiceIdentifier(serviceClass, Collections.<String, Object>emptyMap(), serviceId));
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getRemoteProxy(ServiceIdentifier serviceIdentifier) {
        RequestHandler requestHandler = remoteConfig.createOutgoingRequestHandler();
        ClassLoader classLoader = serviceIdentifier.getServiceClass().getClassLoader();
        Class<?>[] interfaces = new Class<?>[]{ serviceIdentifier.getServiceClass() };
        RemoteServiceHandler remoteRequestHandler =
            new RemoteServiceHandler(serviceIdentifier, requestHandler, principal, credentials);
        return (T) Proxy.newProxyInstance(classLoader, interfaces, remoteRequestHandler);
    }
  
    public String createConnector(String domainType) throws ConnectorValidationFailedException {
        return createConnector(domainType, new HashMap<String, Object>());
    }

    public String createConnector(String domainType, Map<String, Object> properties)
        throws ConnectorValidationFailedException {
        ConnectorDescription connectorDescription =
            new ConnectorDescription(domainType, "external-connector-proxy", new HashMap<String, String>(),
                properties);
        return getConnectorManager().create(connectorDescription);
    }

    public void registerConnector(String uuid, Domain connectorInstance) {
        LocalRequestHandler remoteRequestHandler = new LocalRequestHandler(connectorInstance);
        String destination = remoteConfig.registerRequestHandler(remoteRequestHandler, uuid);
        String portId = remoteConfig.getPortId();
        getConnectorRegistry().registerConnector(uuid, portId, destination);
    }

    public void deleteConnector(String id) {
        ConnectorManager connectorManager = getConnectorManager();
        connectorManager.delete(id);
    }

    public void unregisterConnector(String uuid) {
        getConnectorRegistry().unregisterConnector(uuid);
    }

    private ConnectorManager getConnectorManager() {
        return getRemoteProxy(ConnectorManager.class);
    }

    private ProxyConnectorRegistry getConnectorRegistry() {
        return getRemoteProxy(ProxyConnectorRegistry.class);
    }

}
