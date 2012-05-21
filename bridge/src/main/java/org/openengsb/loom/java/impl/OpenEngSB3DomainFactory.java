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
import org.openengsb.loom.java.impl.jms.JmsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenEngSB3DomainFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenEngSB3DomainFactory.class);

    private JmsConfig jmsConfig;

    public OpenEngSB3DomainFactory(JmsConfig config) throws JMSException {
        jmsConfig = config;
    }

    public String registerConnector(String domainType, Domain connectorInstance)
        throws ConnectorValidationFailedException, JMSException {

        LocalRequestHandler remoteRequestHandler = new LocalRequestHandler(connectorInstance);
        ConnectorDescription connectorDescription = new ConnectorDescription(
            domainType, "external-connector-proxy",
            new HashMap<String, String>(), new HashMap<String, Object>());
        ConnectorConfiguration config = jmsConfig.createConnectorHandler(remoteRequestHandler,
            connectorDescription);
        ConnectorManager cm = getRemoteProxy(ConnectorManager.class, null);
        cm.createWithId(config.getConnectorId(), config.getContent());
        return config.getConnectorId();
    }

    @SuppressWarnings("unchecked")
    public <T> T getRemoteProxy(Class<T> serviceType, final String serviceId) {
        RequestHandler requestHandler = jmsConfig.createRequestHandler();
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ serviceType },
            new RemoteServiceHandler(serviceId, requestHandler));
    }

    public void unregisterConnector(Object connectorInstance) {

    }
}
