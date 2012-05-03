package org.openengsb.loom.java.impl;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.codehaus.jackson.map.ObjectMapper;

public class JmsJsonMessageWrapper implements JmsMessageWrapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Session session;

    public JmsJsonMessageWrapper(Session session) {
        this.session = session;
    }

    @Override
    public Message marshal(Object o) throws IOException, JMSException {
        String text = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        return session.createTextMessage(text);
    }

    @Override
    public <T> T unmarshal(Message message, Class<T> type) throws IOException, JMSException {
        String text = ((TextMessage) message).getText();
        return OBJECT_MAPPER.readValue(text, type);
    }
}
