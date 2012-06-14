package org.openengsb.loom.java;


public interface ProtocolHandler {

    String registerRequestHandler(LocalRequestHandler requestHandler, String uuid);

    RequestHandler createOutgoingRequestHandler();

    void destroy();

    String getPortId();

}
