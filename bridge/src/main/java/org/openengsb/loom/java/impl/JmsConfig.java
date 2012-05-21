package org.openengsb.loom.java.impl;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsConfig.class);

    Connection connection;
    Session session;

    MessageProducer receiveQueueProducer;

    Queue replyQueue;

    QueueMap<String, Message> replyMessageQueue = new QueueMap<String, Message>();

    JmsMessageWrapper wrapper;

    public JmsConfig(String baseURL) throws JMSException{
        initActiveMQ(baseURL);
        wrapper = new JmsJsonMessageWrapper(session);
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

}
