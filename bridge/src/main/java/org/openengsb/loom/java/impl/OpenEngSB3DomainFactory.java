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

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.TextMessage;

import org.codehaus.jackson.map.ObjectMapper;
import org.openengsb.connector.usernamepassword.Password;
import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.Domain;
import org.openengsb.core.api.model.BeanDescription;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodCallRequest;
import org.openengsb.core.api.remote.MethodResult;
import org.openengsb.core.api.remote.MethodResultMessage;
import org.openengsb.core.api.security.model.SecureRequest;
import org.openengsb.core.api.security.model.SecureResponse;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class OpenEngSB3DomainFactory {

    private final class RemoteServiceHandler implements InvocationHandler {
        private final String serviceId;

        private final JmsMessageWrapper wrapper;

        public RemoteServiceHandler(String serviceId, JmsMessageWrapper wrapper) {
            this.serviceId = serviceId;
            this.wrapper = wrapper;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args == null) {
                args = new Object[0];
            }
            MethodCall methodCall = createMethodCall(method, args, serviceId);
            SecureRequest wrapped = wrapMethodCall(methodCall);
            SecureResponse response = sendTestAndGetResponse(wrapped);
            return response.getMessage().getResult().getArg();
        }

        private SecureResponse sendTestAndGetResponse(SecureRequest request) throws JMSException, InterruptedException,
            IOException {
            Message message = wrapper.marshal(request);
            String correlationId = UUID.randomUUID().toString();
            message.setJMSCorrelationID(correlationId);
            message.setJMSReplyTo(jmsConfig.replyQueue);
            jmsConfig.receiveQueueProducer.send(message);
            Message result = jmsConfig.replyMessageQueue.poll(correlationId);
            return wrapper.unmarshal(result, SecureResponse.class);
        }

        private SecureRequest wrapMethodCall(MethodCall methodCall) {
            MethodCallRequest methodCallRequest = new MethodCallRequest(methodCall);
            methodCallRequest.setAnswer(true);
            SecureRequest result =
                SecureRequest.create(methodCallRequest, "admin", BeanDescription.fromObject(new Password("password")));
            return result;
        }

        private MethodCall createMethodCall(Method method, Object[] args, String serviceId) {
            MethodCall methodCall = new MethodCall(method.getName(), args);
            Map<String, String> metadata = new HashMap<String, String>();
            if (serviceId != null) {
                metadata.put("serviceFilter",
                    String.format("(&(%s=%s)(%s=%s))", Constants.OBJECTCLASS, method.getDeclaringClass().getName(),
                        "id", serviceId));
            } else {
                metadata.put("serviceFilter",
                    String.format("(%s=%s)", Constants.OBJECTCLASS, method.getDeclaringClass().getName()));
            }

            methodCall.setMetaData(metadata);
            return methodCall;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenEngSB3DomainFactory.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JmsConfig jmsConfig;

    public OpenEngSB3DomainFactory(JmsConfig config) throws JMSException {
        jmsConfig = config;
    }

    public String registerConnector(String domainType, final Domain connectorInstance)
        throws ConnectorValidationFailedException, JMSException {
        ConnectorManager cm = getRemoteProxy(ConnectorManager.class, null);
        String queuename = UUID.randomUUID().toString();
        Queue connectorIncQueue = jmsConfig.session.createQueue(queuename);
        MessageConsumer createConsumer = jmsConfig.session.createConsumer(connectorIncQueue);
        createConsumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                LOGGER.info("received: " + message);
                TextMessage tm = (TextMessage) message;
                String text;
                try {
                    text = tm.getText();
                } catch (JMSException e) {
                    LOGGER.error("Exception when retrieving message", e);
                    return;
                }
                LOGGER.info("received text {}", text);
                SecureRequest request;
                try {
                    request = OBJECT_MAPPER.readValue(text, SecureRequest.class);
                } catch (IOException e) {
                    LOGGER.error("Exception when parsing message", e);
                    return;
                }
                LOGGER.info(request.toString());
                MethodResult result;
                MethodCall methodCall = request.getMessage().getMethodCall();
                if (methodCall.getMethodName().contains("set")) {
                    result = MethodResult.newVoidResult();
                } else {
                    List<String> classes = methodCall.getClasses();
                    Class<?>[] argTypes = new Class<?>[classes.size()];
                    for (int i = 0; i < classes.size(); i++) {
                        try {
                            argTypes[i] = Class.forName(classes.get(i));
                        } catch (ClassNotFoundException e) {
                            LOGGER.error("unable to find class", e);
                            return;
                        }
                    }
                    Method m;
                    try {
                        m = connectorInstance.getClass().getMethod(methodCall.getMethodName(), argTypes);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        Object invoke = m.invoke(connectorInstance, methodCall.getArgs());
                        result = new MethodResult(invoke);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e.getCause());
                    }
                }

                String callId = request.getMessage().getCallId();
                SecureResponse create =
                    SecureResponse.create(new MethodResultMessage(result, callId));
                String json;
                try {
                    json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(create);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Queue createQueue;
                try {
                    createQueue = jmsConfig.session.createQueue(callId);
                } catch (JMSException e) {
                    LOGGER.error("error creating result-queue", e);
                    return;
                }
                MessageProducer producer;
                try {
                    producer = jmsConfig.session.createProducer(createQueue);
                    producer.send(jmsConfig.session.createTextMessage(json));
                } catch (JMSException e) {
                    LOGGER.error("error creating result-queue", e);
                    return;
                }
            }
        });
        MessageListener messageListener = createConsumer.getMessageListener();
        System.out.println(messageListener);
        String destination = "tcp://127.0.0.1:6549?" + queuename;
        Map<String, String> attr =
            ImmutableMap.of("portId", "jms-json", "destination", destination, "serviceId",
                queuename);
        Map<String, Object> props = ImmutableMap.of();
        cm.createWithId(queuename, new ConnectorDescription("example", "external-connector-proxy", attr, props));
        return queuename;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRemoteProxy(Class<T> serviceType, final String serviceId) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ serviceType },
            new RemoteServiceHandler(serviceId, jmsConfig.wrapper));
    }

    public void unregisterConnector(Object connectorInstance) {

    }
}
