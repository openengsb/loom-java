package org.openengsb.loom.java;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.openengsb.core.api.model.BeanDescription;
import org.openengsb.core.api.model.OpenEngSBModel;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodCallMessage;
import org.openengsb.core.api.remote.MethodResult.ReturnType;
import org.openengsb.core.api.remote.MethodResultMessage;
import org.openengsb.core.api.security.Credentials;
import org.openengsb.core.common.util.JsonUtils;
import org.openengsb.core.common.util.ModelUtils;
import org.openengsb.loom.java.util.ArgumentUtils;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteServiceHandler implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteServiceHandler.class);

    protected String serviceId;
    private RequestHandler requestHandler;

    private String principal;
    private Credentials credentials;

    public RemoteServiceHandler(String serviceId, RequestHandler requestHandler, String principal, Credentials credentials) {
        this.serviceId = serviceId;
        this.requestHandler = requestHandler;
        this.principal = principal;
        this.credentials = credentials;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null) {
            args = new Object[0];
        }
        ArgumentUtils.wrapModels(args);
        MethodCall methodCall = createMethodCall(method, args, serviceId);
        MethodCallMessage wrapped = wrapMethodCall(methodCall);
        MethodResultMessage response = requestHandler.process(wrapped);
        if (response.getResult().getType().equals(ReturnType.Object)) {
            JsonUtils.convertResult(response);
        }
        if (response.getResult().getType().equals(ReturnType.Exception)) {
            LOGGER.error(response.getResult().getClassName() + " - " + response.getResult().getArg());
            throw new RemoteException(response.getResult().getClassName());
        }
        Object resultObject = response.getResult().getArg();
        resultObject = ArgumentUtils.unwrapModel(resultObject);
        return resultObject;
    }

    private MethodCallMessage wrapMethodCall(MethodCall methodCall) {
        MethodCallMessage methodCallRequest = new MethodCallMessage(methodCall);
        methodCallRequest.setPrincipal(principal);
        methodCallRequest.setCredentials(BeanDescription.fromObject(credentials));
        return methodCallRequest;
    }

    private MethodCall createMethodCall(Method method, Object[] args, String serviceId) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof OpenEngSBModel) {
                args[i] = ModelUtils.generateWrapperOutOfModel((OpenEngSBModel) args[i]);
            }
        }
        MethodCall methodCall = new MethodCall(method.getName(), args);
        Map<String, String> metadata = new HashMap<String, String>();
        if (serviceId != null) {
            metadata.put("serviceFilter",
                String.format("(&(%s=%s)(%s=%s))", Constants.OBJECTCLASS, method.getDeclaringClass().getName(),
                    "service.pid", serviceId));
        } else {
            metadata.put("serviceFilter",
                String.format("(%s=%s)", Constants.OBJECTCLASS, method.getDeclaringClass().getName()));
        }
        metadata.put("contextId", "root");
        methodCall.setMetaData(metadata);
        return methodCall;
    }
}
