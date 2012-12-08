package org.openengsb.loom.java.jms;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig.Feature;
import org.openengsb.core.api.remote.MethodCallMessage;
import org.openengsb.core.api.remote.MethodResult;
import org.openengsb.core.api.remote.MethodResultMessage;
import org.openengsb.loom.java.LocalRequestHandler;
import org.openengsb.loom.java.ProtocolHandler;
import org.openengsb.loom.java.RequestHandler;
import org.openengsb.loom.java.util.QueueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsProtocolHandler implements ProtocolHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JmsProtocolHandler.class);
    private final String applicationid;

    private class ReplyQueueListener implements MessageListener {
        @Override
        public void onMessage(Message message) {
            LOGGER.info("got message on reply-queue: {}", message.toString());
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
        public MethodResultMessage process(MethodCallMessage request) throws Exception {
            Message message = marshal(request);
            String correlationId = UUID.randomUUID().toString();
            message.setJMSCorrelationID(correlationId);
            Message result = sendAndReceive(message, correlationId);
            return unmarshal(result, MethodResultMessage.class);
        }

        private Message sendAndReceive(Message message, String correlationId) throws JMSException, InterruptedException {
            LOGGER.info("sending message to queue recieve and expect answer to queue: \"{}\" with corr-id {}", replyQueue, correlationId);
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
            LOGGER.info("got message for connector: {}", message.toString());
            MethodCallMessage request;
            try {
                request = unmarshal(message, MethodCallMessage.class);
            } catch (JMSException e) {
                LOGGER.error("Exception when parsing message", e);
                return;
            } catch (IOException e) {
                LOGGER.error("Exception when parsing message", e);
                return;
            }
            LOGGER.info("unmarshalled to {}", request.toString());
            MethodResult result = remoteRequestHandler.process(request.getMethodCall());
            String callId = request.getCallId();
            MethodResultMessage response = new MethodResultMessage(result, callId);
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .disable(Feature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(Feature.INDENT_OUTPUT)
            ;

    private Connection connection;
    private Session session;

    private MessageProducer receiveQueueProducer;
    private Queue replyQueue;
    private QueueMap<String, Message> replyMessageQueue = new QueueMap<String, Message>();

    public JmsProtocolHandler(String baseURL, String applicationid) throws JMSException {
        this.applicationid = applicationid;
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
            identifier = String.format("CLIENT-%s-%s",
                    applicationid, InetAddress.getLocalHost().toString());
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        replyQueue = session.createQueue(identifier);
        MessageConsumer consumer = session.createConsumer(replyQueue);
        LOGGER.info("listening on queue {}", replyQueue);
        consumer.setMessageListener(new ReplyQueueListener());

    }

    private void initReceiveQueue() throws JMSException {
        LOGGER.debug("creating receive-queue");
        Destination destination = session.createQueue("receive");
        receiveQueueProducer = session.createProducer(destination);
        LOGGER.info("now listening on queue \"receive\"");
    }

    @Override
    public void destroy() {
        try {
            session.close();
            connection.stop();
            connection.close();
        } catch (JMSException e) {
            LOGGER.error("error while destroying jms-connections", e);
        }
    }

    @Override
    public RequestHandler createOutgoingRequestHandler() {
        return new JmsRemoteRequestHandler();
    }

    @Override
    public String registerRequestHandler(LocalRequestHandler remoteRequestHandler, String uuid) {
        String queuename = uuid;
        try {
            Queue connectorIncQueue = session.createQueue(queuename);
            MessageConsumer createConsumer = session.createConsumer(connectorIncQueue);
            createConsumer.setMessageListener(new ConnectorMessageListener(remoteRequestHandler));
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        String destination = "tcp://127.0.0.1:6549?" + queuename;
        return destination;
    }

    @Override
    public String getPortId() {
        return "jms-json";
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
