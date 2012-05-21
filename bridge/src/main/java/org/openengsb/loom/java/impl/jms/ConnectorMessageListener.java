package org.openengsb.loom.java.impl.jms;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;

import org.openengsb.core.api.remote.MethodResult;
import org.openengsb.core.api.remote.MethodResultMessage;
import org.openengsb.core.api.security.model.SecureRequest;
import org.openengsb.core.api.security.model.SecureResponse;
import org.openengsb.loom.java.impl.LocalRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConnectorMessageListener implements MessageListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorMessageListener.class);

    private final LocalRequestHandler remoteRequestHandler;

    private JmsConfig jmsConfig;

    ConnectorMessageListener(LocalRequestHandler remoteRequestHandler, JmsConfig jmsConfig) {
        this.remoteRequestHandler = remoteRequestHandler;
        this.jmsConfig = jmsConfig;
    }

    @Override
    public void onMessage(Message message) {
        SecureRequest request;
        try {
            request = jmsConfig.wrapper.unmarshal(message, SecureRequest.class);
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
        SecureResponse create = SecureResponse.create(new MethodResultMessage(result, callId));
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
            producer.send(jmsConfig.wrapper.marshal(create));
        } catch (JMSException e) {
            LOGGER.error("error creating result-queue", e);
            return;
        } catch (IOException e) {
            LOGGER.error("error creating result-queue", e);
            return;
        }
    }
}
