package org.openengsb.loom.java.impl.jms;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonMessageWrapper implements MessageWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonMessageWrapper.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private Session session;

    public JsonMessageWrapper(Session session) {
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
        try {
            return OBJECT_MAPPER.readValue(text, type);
        } catch (IOException e) {
            LOGGER.error("cannot parse: {}", text);
            LOGGER.error("because", e);
            throw e;
        }
    }
}
