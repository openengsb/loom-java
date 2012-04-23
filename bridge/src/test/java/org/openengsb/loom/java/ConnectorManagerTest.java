package org.openengsb.loom.java;

import static org.junit.Assert.assertThat;

import java.util.Collection;

import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.openengsb.core.api.security.service.UserDataManager;
import org.openengsb.loom.java.impl.OpenEngSB3DomainFactory;

public class ConnectorManagerTest {

    private static final String baseURL = "failover:(tcp://localhost:6549)?timeout=6000";

    @Test
    public void retrieveServiceProxy() throws Exception {
        OpenEngSB3DomainFactory domainFactory = new OpenEngSB3DomainFactory(baseURL);
        UserDataManager userDataManager = domainFactory.getRemoteProxy(UserDataManager.class);
        Collection<String> userList = userDataManager.getUserList();
        assertThat(userList, CoreMatchers.hasItems("admin", "user"));
    }
    
}
