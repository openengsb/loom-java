package org.openengsb.loom.java;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.openengsb.connector.usernamepassword.Password;
import org.openengsb.core.api.model.BeanDescription;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodCallRequest;
import org.openengsb.core.api.security.model.SecureRequest;
import org.openengsb.core.api.security.model.SecureResponse;
import org.osgi.framework.Constants;

public class RemoteServiceHandler implements InvocationHandler {

    protected String serviceId;
    private RequestHandler requestHandler;

    public RemoteServiceHandler(String serviceId, RequestHandler requestHandler) {
        this.serviceId = serviceId;
        this.requestHandler = requestHandler;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null) {
            args = new Object[0];
        }
        MethodCall methodCall = createMethodCall(method, args, serviceId);
        SecureRequest wrapped = wrapMethodCall(methodCall);
        SecureResponse response = requestHandler.process(wrapped);
        return response.getMessage().getResult().getArg();
    }

    private SecureRequest wrapMethodCall(MethodCall methodCall) {
        MethodCallRequest methodCallRequest = new MethodCallRequest(methodCall);
        methodCallRequest.setAnswer(true);
        SecureRequest result =
            SecureRequest.create(methodCallRequest, "admin", BeanDescription.fromObject(new Password("password")));
        return result;
    }

    private MethodCall createMethodCall(Method method, Object[] args, String serviceId) {
        MethodCall methodCall = new MethodCall(method.getName(), args);
        Map<String, String> metadata = new HashMap<String, String>();
        if (serviceId != null) {
            metadata.put("serviceFilter",
                String.format("(&(%s=%s)(%s=%s))", Constants.OBJECTCLASS, method.getDeclaringClass().getName(),
                    "id", serviceId));
        } else {
            metadata.put("serviceFilter",
                String.format("(%s=%s)", Constants.OBJECTCLASS, method.getDeclaringClass().getName()));
        }

        methodCall.setMetaData(metadata);
        return methodCall;
    }
}
