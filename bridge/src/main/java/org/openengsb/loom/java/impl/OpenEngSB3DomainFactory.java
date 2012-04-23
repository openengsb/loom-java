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
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
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
import org.openengsb.connector.usernamepassword.Password;
import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.Domain;
import org.openengsb.core.api.remote.GenericObjectSerializer;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodCallRequest;
import org.openengsb.core.api.security.model.SecureRequest;
import org.openengsb.core.api.security.model.SecureResponse;
import org.openengsb.core.common.json.JsonObjectSerializer;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenEngSB3DomainFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenEngSB3DomainFactory.class);

    private Connection connection;
    private Session session;

    private MessageProducer receiveQueueProducer;

    private GenericObjectSerializer objectSerializer = new JsonObjectSerializer();

    private String identifier;

    private Queue replyQueue;

    private QueueMap<String, Message> replyMessageQueue = new QueueMap<String, Message>();

    public OpenEngSB3DomainFactory(String baseURL) throws JMSException {
        LOGGER.info("initializing domain factory for URL: {}", baseURL);
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(baseURL);
        LOGGER.info("creating connection");
        connection = connectionFactory.createConnection();
        LOGGER.info("starting connection");
        connection.start();
        LOGGER.info("creating session");
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        LOGGER.info("creating receive-queue");
        receiveQueueProducer = createProducerForQueue("receive");
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
                try {
                    replyMessageQueue.put(message.getJMSCorrelationID(), message);
                } catch (JMSException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        });
    }

    private MessageProducer createProducerForQueue(String name) throws JMSException {
        Destination destination = session.createQueue(name);
        return session.createProducer(destination);
    }

    private MessageConsumer createConsumerForQueue(String name) throws JMSException {
        Destination queue = session.createQueue(name);
        return session.createConsumer(queue);
    }

    private MessageConsumer createConsumerForQueue(String name, MessageListener listener) throws JMSException {
        MessageConsumer result = createConsumerForQueue(name);
        result.setMessageListener(listener);
        return result;
    }

    // public void sendMessage(String queue, String message) throws JMSException {
    // LOGGER.info("sending message {}", message);
    // LOGGER.info("to queue {}", queue);
    // TextMessage message2 = session.createTextMessage(message);
    // MessageProducer resultProducer = createProducerForQueue(queue);
    // resultProducer.send(message2);
    //
    // }

    public void destroy() throws JMSException {
        session.close();
        connection.stop();
        connection.close();
    }

    public <T extends Domain> void registerConnector(T connectorInstance) {
        ConnectorManager remoteProxy = getRemoteProxy(ConnectorManager.class);

    }

    @SuppressWarnings("unchecked")
    public <T> T getRemoteProxy(Class<T> serviceType) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ serviceType },
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String text = marshal(method, args);
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
                    SecureResponse response = objectSerializer.parse(resultString, SecureResponse.class);
                    return response.getMessage().getResult().getArg();
                }
            });
    }

    private String marshal(Method method, Object[] args) throws IOException {
        MethodCall methodCall = new MethodCall(method.getName(), args);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("serviceFilter",
            String.format("(%s=%s)", Constants.OBJECTCLASS, method.getDeclaringClass().getName()));
        methodCall.setMetaData(metadata);
        MethodCallRequest methodCallRequest = new MethodCallRequest(methodCall);
        methodCallRequest.setAnswer(true);
        SecureRequest create = SecureRequest.create(methodCallRequest, "admin", new Password("password"));
        return objectSerializer.serializeToString(create);
    }

    public void unregisterConnector(Object connectorInstance) {

    }
}
