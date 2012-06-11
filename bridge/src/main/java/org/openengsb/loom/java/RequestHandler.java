package org.openengsb.loom.java;

import org.openengsb.core.api.remote.MethodCallMessage;
import org.openengsb.core.api.remote.MethodResultMessage;

public interface RequestHandler {

    MethodResultMessage process(MethodCallMessage request) throws Exception;

}
