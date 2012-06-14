package org.openengsb.loom.java.util;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.openengsb.core.api.model.OpenEngSBModel;
import org.openengsb.core.api.model.OpenEngSBModelEntry;
import org.openengsb.core.api.model.OpenEngSBModelWrapper;
import org.openengsb.core.common.util.ModelUtils;

public class ArgumentUtils {

    public static Object[] wrapModels(Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof OpenEngSBModel) {
                args[i] = wrapModel(args[i]);
            }
        }
        return args;
    }

    public static Object[] unwrapModels(Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof OpenEngSBModelWrapper) {
                args[i] = unwrapModel(args[i]);
            }
        }
        return args;
    }

    public static Object wrapModel(Object arg) {
        if (!(arg instanceof OpenEngSBModel)) {
            return arg;
        }
        OpenEngSBModel object = (OpenEngSBModel) arg;
        OpenEngSBModelWrapper wrapper = new OpenEngSBModelWrapper();
        Class<?>[] interfaces = object.getClass().getInterfaces();
        wrapper.setModelClass(interfaces[interfaces.length - 1].getName());
        wrapper.setEntries(object.getOpenEngSBModelEntries());
        return wrapper;
    }

    public static Object unwrapModel(Object arg) {
        if (!(arg instanceof OpenEngSBModelWrapper)) {
            return arg;
        }
        OpenEngSBModelWrapper wrapper = (OpenEngSBModelWrapper) arg;
        Class<?> clazz;
        try {
            clazz = Class.forName(wrapper.getModelClass());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        OpenEngSBModelEntry[] entryArray =
            wrapper.getEntries().toArray(new OpenEngSBModelEntry[wrapper.getEntries().size()]);
        Object instance = ModelUtils.createModelObject(clazz, entryArray);

        return instance;
    }
}
