package org.openengsb.loom.java;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.openengsb.connector.usernamepassword.Password;
import org.openengsb.core.api.model.BeanDescription;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodCallMessage;
import org.openengsb.core.api.remote.MethodResult.ReturnType;
import org.openengsb.core.api.remote.MethodResultMessage;
import org.openengsb.core.common.util.JsonUtils;
import org.openengsb.loom.java.util.ArgumentUtils;
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
        ArgumentUtils.wrapModels(args);
        MethodCall methodCall = createMethodCall(method, args, serviceId);
        MethodCallMessage wrapped = wrapMethodCall(methodCall);
        MethodResultMessage response = requestHandler.process(wrapped);
        if (response.getResult().getType().equals(ReturnType.Object)) {
            JsonUtils.convertResult(response);
        }
        if(response.getResult().getType().equals(ReturnType.Exception)){
            throw new RemoteException(response.getResult().getClassName());
        }
        Object resultObject = response.getResult().getArg();
        resultObject = ArgumentUtils.unwrapModel(resultObject);
        return resultObject;
    }

    private MethodCallMessage wrapMethodCall(MethodCall methodCall) {
        MethodCallMessage methodCallRequest = new MethodCallMessage(methodCall);
        methodCallRequest.setPrincipal("admin");
        methodCallRequest.setCredentials(BeanDescription.fromObject(new Password("password")));
        return methodCallRequest;
    }

    private MethodCall createMethodCall(Method method, Object[] args, String serviceId) {
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

        methodCall.setMetaData(metadata);
        return methodCall;
    }
}
