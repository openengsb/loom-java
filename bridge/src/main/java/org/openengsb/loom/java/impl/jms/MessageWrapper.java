package org.openengsb.loom.java.impl.jms;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.Message;

public interface MessageWrapper {

    Message marshal(Object o) throws IOException, JMSException;

    <T> T unmarshal(Message message, Class<T> type) throws IOException, JMSException;

}
