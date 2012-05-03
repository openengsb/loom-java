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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openengsb.connector.usernamepassword.Password;
import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.Domain;
import org.openengsb.core.api.model.BeanDescription;
import org.openengsb.core.api.model.ConnectorDefinition;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenEngSB3DomainFactory.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Connection connection;
    private Session session;

    private MessageProducer receiveQueueProducer;

    private Queue replyQueue;

    private QueueMap<String, Message> replyMessageQueue = new QueueMap<String, Message>();

    public OpenEngSB3DomainFactory(String baseURL) throws JMSException {
        initActiveMQ(baseURL);
        initMainQueues();
    }

    private void initActiveMQ(String baseURL) throws JMSException {
        LOGGER.info("initializing domain factory for URL: {}", baseURL);
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(baseURL);
        LOGGER.info("creating connection");
        connection = connectionFactory.createConnection();
        LOGGER.info("starting connection");
        connection.start();
        LOGGER.info("creating session");
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    private void initMainQueues() throws JMSException {
        initReceiveQueue();
        initReplyQueue();
    }

    private void initReplyQueue() throws JMSException {
        String identifier;
        try {
            identifier = "CLIENT-" + InetAddress.getLocalHost().toString();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        replyQueue = session.createQueue(identifier);
        MessageConsumer consumer = session.createConsumer(replyQueue);
        LOGGER.info("listening on queue {}", replyQueue);
        consumer.setMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                LOGGER.info(message.toString());
                String jmsCorrelationID;
                try {
                    jmsCorrelationID = message.getJMSCorrelationID();
                } catch (JMSException e) {
                    LOGGER.error("error processing the message", e);
                    return;
                }
                replyMessageQueue.put(jmsCorrelationID, message);
            }
        });
    }

    private void initReceiveQueue() throws JMSException {
        LOGGER.info("creating receive-queue");
        Destination destination = session.createQueue("receive");
        receiveQueueProducer =  session.createProducer(destination);
    }

    public void destroy() throws JMSException {
        session.close();
        connection.stop();
        connection.close();
    }

    public String registerConnector(String domainType, final Domain connectorInstance)
        throws ConnectorValidationFailedException, JMSException {
        ConnectorManager cm = getRemoteProxy(ConnectorManager.class, null);
        ConnectorDefinition def = ConnectorDefinition.generate("example", "external-connector-proxy");
        String queuename = def.getInstanceId();
        queuename = "example-remote";
        Queue connectorIncQueue = session.createQueue(queuename);
        MessageConsumer createConsumer = session.createConsumer(connectorIncQueue);
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
                    createQueue = session.createQueue(callId);
                } catch (JMSException e) {
                    LOGGER.error("error creating result-queue", e);
                    return;
                }
                MessageProducer producer;
                try {
                    producer = session.createProducer(createQueue);
                    producer.send(session.createTextMessage(json));
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
                def.getInstanceId());
        Map<String, Object> props = ImmutableMap.of();

        cm.create(def, new ConnectorDescription(attr, props));
        return def.getInstanceId();
        // return queuename;
    }

    @SuppressWarnings("unchecked")
    public <T> T getRemoteProxy(Class<T> serviceType, final String serviceId) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ serviceType },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if (args == null) {
                        args = new Object[0];
                    }
                    String text = marshal(method, args, serviceId);
                    LOGGER.info("sending: {}", text);
                    TextMessage message = session.createTextMessage(text);
                    String correlationId = UUID.randomUUID().toString();
                    message.setJMSCorrelationID(correlationId);
                    message.setJMSReplyTo(replyQueue);
                    receiveQueueProducer.send(message);
                    Thread.sleep(1000);
                    Message result = replyMessageQueue.poll(correlationId);
                    TextMessage textResult = (TextMessage) result;
                    String resultString = textResult.getText();
                    LOGGER.info("received response: {}", resultString);
                    SecureResponse response = OBJECT_MAPPER.readValue(resultString, SecureResponse.class);
                    return response.getMessage().getResult().getArg();
                }
            });
    }

    private String marshal(Method method, Object[] args, String serviceId) throws IOException {
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
        MethodCallRequest methodCallRequest = new MethodCallRequest(methodCall);
        methodCallRequest.setAnswer(true);
        SecureRequest create =
            SecureRequest.create(methodCallRequest, "admin", BeanDescription.fromObject(new Password("password")));
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(create);
    }

    public void unregisterConnector(Object connectorInstance) {

    }
}
