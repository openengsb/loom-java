package org.openengsb.loom.java.impl;

import java.io.IOException;

import javax.jms.JMSException;
import javax.jms.Message;

public interface JmsMessageWrapper {

    Message marshal(Object o) throws IOException, JMSException;

    <T> T unmarshal(Message message, Class<T> type) throws IOException, JMSException;

}
