package org.openengsb.loom.java;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.HashMap;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.security.service.UserDataManager;
import org.openengsb.domain.example.ExampleDomain;
import org.openengsb.loom.java.impl.OpenEngSB3DomainFactory;
import org.openengsb.loom.java.impl.jms.JmsProtocolHandler;

import com.google.common.collect.ImmutableMap;

public class ConnectorManagerUT {

    private OpenEngSB3DomainFactory domainFactory;

    private static final String baseURL = "failover:(tcp://localhost:6549)?timeout=6000";

    private JmsProtocolHandler jmsConfig;

    @Before
    public void setUp() throws Exception {
        jmsConfig = new JmsProtocolHandler(baseURL);
        domainFactory = new OpenEngSB3DomainFactory(jmsConfig);
    }

    @After
    public void tearDown() throws Exception {
        jmsConfig.destroy();
    }

    @Test
    public void retrieveServiceProxy() throws Exception {
        UserDataManager userDataManager = domainFactory.getRemoteProxy(UserDataManager.class, null);
        Collection<String> userList = userDataManager.getUserList();
        assertThat(userList, CoreMatchers.hasItems("admin", "user"));
    }

    @Test
    public void createConnector() throws Exception {
        ConnectorManager cm = domainFactory.getRemoteProxy(ConnectorManager.class, null);
        ConnectorDescription connectorDescription = new ConnectorDescription("example", "example",
            ImmutableMap.of("prefix", "<><>", "level", "info"),
            new HashMap<String, Object>());
        cm.create(connectorDescription);
    }

    @Test
    public void createConnectorProxy() throws Exception {
        ExampleDomain handler = new ExampleConnector();
        String uuid = domainFactory.createConnector("example", handler);
        final ExampleDomain self = domainFactory.getRemoteProxy(ExampleDomain.class, uuid);
        assertThat(self.doSomethingWithMessage("asdf"), is("42"));
        domainFactory.deleteConnector(uuid);
        Thread thread = new Thread() {
            public void run() {
                self.doSomethingWithMessage("stuff");
            };
        };
        thread.start();
        thread.join(2000);
        assertThat("It seems the example-connector has not been deleted", thread.isAlive(), is(true));
        thread.interrupt();
    }
}
