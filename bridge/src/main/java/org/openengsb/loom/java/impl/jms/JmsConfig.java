package org.openengsb.loom.java.impl.jms;

import java.net.InetAddress;
import java.net.UnknownHostException;
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

import org.apache.activemq.ActiveMQConnectionFactory;
import org.openengsb.core.api.model.ConnectorConfiguration;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.loom.java.impl.LocalRequestHandler;
import org.openengsb.loom.java.impl.QueueMap;
import org.openengsb.loom.java.impl.RemoteConfig;
import org.openengsb.loom.java.impl.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class JmsConfig implements RemoteConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsConfig.class);

    private Connection connection;
    Session session;

    MessageProducer receiveQueueProducer;

    Queue replyQueue;

    QueueMap<String, Message> replyMessageQueue = new QueueMap<String, Message>();

    MessageWrapper wrapper;

    public JmsConfig(String baseURL) throws JMSException {
        initActiveMQ(baseURL);
        wrapper = new JsonMessageWrapper(session);
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
        receiveQueueProducer = session.createProducer(destination);
    }

    public void destroy() throws JMSException {
        session.close();
        connection.stop();
        connection.close();
    }

    @Override
    public RequestHandler createRequestHandler() {
        return new JmsRemoteRequestHandler(this);
    }

    @Override
    public ConnectorConfiguration createConnectorHandler(LocalRequestHandler remoteRequestHandler,
            ConnectorDescription connectorDescription) {
        String queuename = UUID.randomUUID().toString();
        try {
            Queue connectorIncQueue = session.createQueue(queuename);
            MessageConsumer createConsumer = session.createConsumer(connectorIncQueue);
            createConsumer.setMessageListener(new ConnectorMessageListener(remoteRequestHandler, this));
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        String destination = "tcp://127.0.0.1:6549?" + queuename;
        Map<String, String> attr =
            ImmutableMap.of("portId", "jms-json", "destination", destination, "serviceId", queuename);
        connectorDescription.setAttributes(attr);
        return new ConnectorConfiguration(queuename, connectorDescription);
    }

}
