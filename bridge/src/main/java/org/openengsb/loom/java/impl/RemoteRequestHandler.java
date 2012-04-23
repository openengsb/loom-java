/**
 * Licensed to the Austrian Association for Software Tool Integration (AASTI)
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. The AASTI licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openengsb.loom.java.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.openengsb.core.api.Domain;
import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodResult;
import org.openengsb.core.api.remote.MethodResult.ReturnType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteRequestHandler<T extends Domain> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteRequestHandler.class);
    private T connector;

    private Map<MethodCall, MethodResult> invocationHistory = new LinkedHashMap<MethodCall, MethodResult>();

    public MethodResult process(MethodCall request) {
        Class<?>[] argTypes = getArgTypes(request);
        Method method;
        try {
            LOGGER.debug("searching for method {} with args {}", request.getMethodName(), argTypes);
            method = connector.getClass().getMethod(request.getMethodName(), argTypes);
        } catch (NoSuchMethodException e) {
            return makeExceptionResult(e);
        }
        try {
            LOGGER.info("invoking method {}", method);
            Object result = method.invoke(connector, request.getArgs());
            if (method.getReturnType().equals(void.class)) {
                MethodResult methodResult = new MethodResult();
                methodResult.setType(ReturnType.Void);
                return methodResult;
            }
            LOGGER.debug("invocation successful");
            MethodResult methodResult = new MethodResult(result);
            invocationHistory.put(request, methodResult);
            return methodResult;
        } catch (InvocationTargetException e) {
            return makeExceptionResult((Exception) e.getTargetException());
        } catch (IllegalArgumentException e) {
            return makeExceptionResult(e);
        } catch (IllegalAccessException e) {
            return makeExceptionResult(new IllegalStateException(e));
        }
    }
    
    private Class<?>[] getArgTypes(MethodCall call) {
        Object[] args = call.getArgs();
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i].getClass();
        }
        return types;
    }

    private MethodResult makeExceptionResult(Exception e) {
        LOGGER.error("Exception occured, making Exception result");
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return new MethodResult(sw.toString(), ReturnType.Exception);
    }

    public Map<MethodCall, MethodResult> getInvocationHistory() {
        return invocationHistory;
    }
}
