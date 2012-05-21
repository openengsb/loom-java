package org.openengsb.loom.java.impl.jms;

import java.util.UUID;

import javax.jms.Message;

import org.openengsb.core.api.security.model.SecureRequest;
import org.openengsb.core.api.security.model.SecureResponse;
import org.openengsb.loom.java.impl.RequestHandler;

final class JmsRemoteRequestHandler implements RequestHandler {

    private JmsConfig jmsConfig;

    public JmsRemoteRequestHandler(JmsConfig jmsConfig) {
        this.jmsConfig = jmsConfig;
    }

    /* (non-Javadoc)
     * @see org.openengsb.loom.java.impl.RequestHandler#process(org.openengsb.core.api.security.model.SecureRequest)
     */
    @Override
    public SecureResponse process(SecureRequest request) throws Exception {
        Message message = jmsConfig.wrapper.marshal(request);
        String correlationId = UUID.randomUUID().toString();
        message.setJMSCorrelationID(correlationId);
        message.setJMSReplyTo(jmsConfig.replyQueue);
        jmsConfig.receiveQueueProducer.send(message);
        Message result = jmsConfig.replyMessageQueue.poll(correlationId);
        return jmsConfig.wrapper.unmarshal(result, SecureResponse.class);
    }

}
