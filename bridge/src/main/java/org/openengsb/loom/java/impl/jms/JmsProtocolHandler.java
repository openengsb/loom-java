package org.openengsb.loom.java.impl.jms;

import java.io.IOException;
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
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.openengsb.core.api.model.ConnectorConfiguration;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.remote.MethodResult;
import org.openengsb.core.api.remote.MethodResultMessage;
import org.openengsb.core.api.security.model.SecureRequest;
import org.openengsb.core.api.security.model.SecureResponse;
import org.openengsb.loom.java.impl.LocalRequestHandler;
import org.openengsb.loom.java.impl.ProtocolHandler;
import org.openengsb.loom.java.impl.QueueMap;
import org.openengsb.loom.java.impl.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class JmsProtocolHandler implements ProtocolHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsProtocolHandler.class);

    private class ReplyQueueListener implements MessageListener {
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
    }

    private class JmsRemoteRequestHandler implements RequestHandler {
        @Override
        public SecureResponse process(SecureRequest request) throws Exception {
            Message message = marshal(request);
            String correlationId = UUID.randomUUID().toString();
            message.setJMSCorrelationID(correlationId);
            Message result = sendAndReceive(message, correlationId);
            return unmarshal(result, SecureResponse.class);
        }

        private Message sendAndReceive(Message message, String correlationId) throws JMSException, InterruptedException {
            message.setJMSReplyTo(replyQueue);
            receiveQueueProducer.send(message);
            return replyMessageQueue.poll(correlationId);
        }
    }

    private class ConnectorMessageListener implements MessageListener {
        private final LocalRequestHandler remoteRequestHandler;

        public ConnectorMessageListener(LocalRequestHandler remoteRequestHandler) {
            this.remoteRequestHandler = remoteRequestHandler;
        }

        @Override
        public void onMessage(Message message) {
            SecureRequest request;
            try {
                request = unmarshal(message, SecureRequest.class);
            } catch (JMSException e) {
                LOGGER.error("Exception when parsing message", e);
                return;
            } catch (IOException e) {
                LOGGER.error("Exception when parsing message", e);
                return;
            }
            LOGGER.info(request.toString());
            MethodResult result = remoteRequestHandler.process(request.getMessage().getMethodCall());
            String callId = request.getMessage().getCallId();
            SecureResponse response = SecureResponse.create(new MethodResultMessage(result, callId));
            try {
                MessageProducer producer = createProducerForQueue(callId);
                producer.send(marshal(response));
            } catch (JMSException e) {
                LOGGER.error("error creating result-queue", e);
                return;
            } catch (IOException e) {
                LOGGER.error("error creating result-queue", e);
                return;
            }
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Connection connection;
    private Session session;

    private MessageProducer receiveQueueProducer;
    private Queue replyQueue;
    private QueueMap<String, Message> replyMessageQueue = new QueueMap<String, Message>();

    public JmsProtocolHandler(String baseURL) throws JMSException {
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
        consumer.setMessageListener(new ReplyQueueListener());

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
    public RequestHandler createOutgoingRequestHandler() {
        return new JmsRemoteRequestHandler();
    }

    @Override
    public ConnectorConfiguration registerRequestHandler(LocalRequestHandler remoteRequestHandler,
            ConnectorDescription connectorDescription) {
        String queuename = UUID.randomUUID().toString();
        try {
            Queue connectorIncQueue = session.createQueue(queuename);
            MessageConsumer createConsumer = session.createConsumer(connectorIncQueue);
            createConsumer.setMessageListener(new ConnectorMessageListener(remoteRequestHandler));
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        String destination = "tcp://127.0.0.1:6549?" + queuename;
        Map<String, String> attr =
            ImmutableMap.of("portId", "jms-json", "destination", destination, "serviceId", queuename);
        connectorDescription.setAttributes(attr);
        return new ConnectorConfiguration(queuename, connectorDescription);
    }

    public MessageProducer createProducerForQueue(String callId) throws JMSException {
        Queue createQueue = session.createQueue(callId);
        return session.createProducer(createQueue);
    }

    public Message marshal(Object o) throws IOException, JMSException {
        String text = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        return session.createTextMessage(text);
    }

    public <T> T unmarshal(Message message, Class<T> type) throws IOException, JMSException {
        String text = ((TextMessage) message).getText();
        try {
            return OBJECT_MAPPER.readValue(text, type);
        } catch (IOException e) {
            LOGGER.error("cannot parse: {}", text);
            LOGGER.error("because", e);
            throw e;
        }
    }
}
