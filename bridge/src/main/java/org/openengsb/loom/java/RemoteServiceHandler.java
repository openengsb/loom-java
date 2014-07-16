package org.openengsb.loom.java;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.openengsb.core.api.model.BeanDescription;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodCallMessage;
import org.openengsb.core.api.remote.MethodResult.ReturnType;
import org.openengsb.core.api.remote.MethodResultMessage;
import org.openengsb.core.api.security.Credentials;
import org.openengsb.loom.java.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RemoteServiceHandler implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteServiceHandler.class);

    private ServiceIdentifier serviceIdentifier;
    private RequestHandler requestHandler;

    private String principal;
    private Credentials credentials;
    
    RemoteServiceHandler(ServiceIdentifier serviceIdentifier, RequestHandler requestHandler, String principal,
            Credentials credentials) {
        this.serviceIdentifier = serviceIdentifier;
        this.requestHandler = requestHandler;
        this.principal = principal;
        this.credentials = credentials;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null) {
            args = new Object[0];
        }
        if(method.getDeclaringClass().equals(Object.class)){
            return method.invoke(this, args);
        }
        MethodCall methodCall = createMethodCall(method, args);
        MethodCallMessage wrapped = wrapMethodCall(methodCall);
        MethodResultMessage response = requestHandler.process(wrapped);
        if (response.getResult().getType().equals(ReturnType.Object)) {
            JsonUtils.convertResult(serviceIdentifier.getServiceClass().getClassLoader(), response);
        }
        if (response.getResult().getType().equals(ReturnType.Exception)) {
            LOGGER.error(response.getResult().getClassName() + " - " + response.getResult().getArg());
            throw new RemoteException(response.getResult().getClassName());
        }
        return response.getResult().getArg();
    }

    private MethodCallMessage wrapMethodCall(MethodCall methodCall) {
        MethodCallMessage methodCallRequest = new MethodCallMessage(methodCall);
        methodCallRequest.setPrincipal(principal);
        methodCallRequest.setCredentials(BeanDescription.fromObject(credentials));
        return methodCallRequest;
    }

    private MethodCall createMethodCall(Method method, Object[] args) {
        MethodCall methodCall = new MethodCall(method.getName(), args);
        Map<String, String> metadata = new HashMap<String, String>();
        metadata.put("serviceFilter", serviceIdentifier.getServiceFilter());
        metadata.put("contextId", "root");
        methodCall.setMetaData(metadata);
        return methodCall;
    }
}
