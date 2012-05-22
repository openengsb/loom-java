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

package org.openengsb.loom.java;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.openengsb.core.api.remote.MethodCall;
import org.openengsb.core.api.remote.MethodResult;
import org.openengsb.core.api.remote.MethodResult.ReturnType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalRequestHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalRequestHandler.class);
    private Object connector;

    public LocalRequestHandler(Object connector) {
        this.connector = connector;
    }

    public MethodResult process(MethodCall request) {
        try {
            return doProcess(request);
        } catch (Exception e) {
            return makeExceptionResult(e);
        }
    }

    private MethodResult doProcess(MethodCall request) throws Exception {
        Class<?>[] argTypes = getArgTypes(request);
        LOGGER.debug("searching for method {} with args {}", request.getMethodName(), argTypes);
        Method method = connector.getClass().getMethod(request.getMethodName(), argTypes);
        LOGGER.info("invoking method {}", method);
        Object result;
        try {
            result = method.invoke(connector, request.getArgs());
        } catch (InvocationTargetException e) {
            throw (Exception) e.getTargetException();
        }
        if (method.getReturnType().equals(void.class)) {
            return MethodResult.newVoidResult();
        }
        LOGGER.debug("invocation successful");
        return new MethodResult(result);
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
}
