package org.openengsb.loom.java.impl;

import org.openengsb.core.api.security.model.SecureRequest;
import org.openengsb.core.api.security.model.SecureResponse;

public interface RequestHandler {

    SecureResponse process(SecureRequest request) throws Exception;

}
