package org.openengsb.loom.java;

import java.util.Map;
import java.util.Map.Entry;

import org.osgi.framework.Constants;

import com.google.common.base.Preconditions;

class ServiceIdentifier {
    private Class<?> serviceClass;
    private Map<String, Object> serviceProperties;
    private String serviceId;
    
    ServiceIdentifier(Class<?> serviceClass, Map<String, Object> serviceProperties, String serviceId) {
        if (serviceClass == null) {
            throw new NullPointerException("The service class cannot be null");
        }
        this.serviceClass = serviceClass;
        this.serviceProperties = serviceProperties;
        this.serviceId = serviceId;
    }
    
    Class<?> getServiceClass() {
        return serviceClass;
    }
    
    String getServiceFilter() {
        StringBuilder sb = new StringBuilder(128);
        sb.append("(&");
        appendFilter(sb, Constants.OBJECTCLASS, serviceClass.getName());
        if (serviceId != null) {
            appendFilter(sb, Constants.SERVICE_PID, serviceId);
        } else {
            for (Entry<String, Object> entry : serviceProperties.entrySet()) {
                appendFilter(sb, entry.getKey(), entry.getValue());
            }
        }
        sb.append(')');
        return sb.toString();
    }
    
    private void appendFilter(StringBuilder sb, String propertyName, Object propertyValue) {
        sb.append('(').append(propertyName).append('=').append(propertyValue).append(')');
    }
}
