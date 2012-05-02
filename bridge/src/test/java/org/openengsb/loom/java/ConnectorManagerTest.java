package org.openengsb.loom.java;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.HashMap;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openengsb.core.api.AliveState;
import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.model.ConnectorDefinition;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.security.service.UserDataManager;
import org.openengsb.domain.example.ExampleDomain;
import org.openengsb.domain.example.event.LogEvent;
import org.openengsb.domain.example.model.ExampleRequestModel;
import org.openengsb.domain.example.model.ExampleResponseModel;
import org.openengsb.loom.java.impl.OpenEngSB3DomainFactory;

import com.google.common.collect.ImmutableMap;

public class ConnectorManagerTest {

    public final class ExampleConnector implements ExampleDomain {
        @Override
        public String getInstanceId() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public AliveState getAliveState() {
            return AliveState.ONLINE;
        }

        @Override
        public ExampleResponseModel doSomethingWithModel(ExampleRequestModel arg0) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String doSomethingWithMessage(String arg0) {
            return "42";
        }

        @Override
        public String doSomethingWithLogEvent(LogEvent arg0) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public String doSomethingWithEnum(ExampleEnum arg0) {
            // TODO Auto-generated method stub
            return null;
        }
    }

    private static final String baseURL = "failover:(tcp://localhost:6549)?timeout=6000";
    private OpenEngSB3DomainFactory domainFactory;

    @Before
    public void setUp() throws Exception {
        domainFactory = new OpenEngSB3DomainFactory(baseURL);
    }

    @After
    public void tearDown() throws Exception {
        domainFactory.destroy();
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
        ConnectorDefinition connectorDefinition = new ConnectorDefinition("example", "example", "test2");
        ConnectorDescription connectorDescription = new ConnectorDescription(
            ImmutableMap.of("prefix", "<><>", "level", "info"),
            new HashMap<String, Object>());
        cm.create(connectorDefinition, connectorDescription);
    }

    @Test
    public void createConnectorProxy() throws Exception {
        ExampleDomain handler = new ExampleConnector();
        String uuid = domainFactory.registerConnector("example", handler);
        ExampleDomain self =
            domainFactory.getRemoteProxy(ExampleDomain.class, "example+external-connector-proxy+" + uuid);
        assertThat(self.doSomethingWithMessage("asdf"), is("42"));
    }
}
