package org.openengsb.loom.java;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openengsb.connector.usernamepassword.Password;
import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.security.service.UserDataManager;
import org.openengsb.domain.example.ExampleDomain;
import org.openengsb.domain.example.model.ExampleRequestModel;
import org.openengsb.domain.example.model.ExampleResponseModel;
import org.openengsb.loom.java.jms.JmsProtocolHandler;

import com.google.common.collect.ImmutableMap;

public class ConnectorManagerUT {

	private static final String baseURL = "failover:(tcp://localhost:6549)?timeout=60000";
	private static final String REPLY_HEADER = "LogServiceCalled with: ";
	
    private ProxyConnectorFactory domainFactory;
    private JmsProtocolHandler jmsConfig;
    
    @Before
    public void setUp() throws Exception {
        jmsConfig = new JmsProtocolHandler(baseURL, "junit");
        domainFactory = new ProxyConnectorFactory(jmsConfig, "admin", new Password("password"));
    }

    @After
    public void tearDown() throws Exception {
        jmsConfig.destroy();
    }

    @Test
    public void testRetrieveServiceProxy() throws Exception {
        UserDataManager userDataManager = domainFactory.getRemoteProxy(UserDataManager.class);
        Collection<String> userList = userDataManager.getUserList();
        assertThat(userList, CoreMatchers.hasItems("admin", "user"));
    }

    @Test
    public void testRetrieveConnectorProxy() {
        String connectorId = createExampleConnector("LooM");
        ExampleDomain connectorProxy = domainFactory.getRemoteProxy(ExampleDomain.class, connectorId);
        String result = connectorProxy.doSomethingWithMessage("aaa");
        assertThat(result, CoreMatchers.equalTo(REPLY_HEADER + "LooM: aaa"));
        deleteExampleConnector(connectorId);
    }

    @Test
    public void testRetrieveConnectorWithProperties() {
        Map<String, Object> properties1 = new HashMap<>();
        properties1.put("propA", "valueA1");
        properties1.put("propB", "valueB1");
        String connectorId1 = createExampleConnector("LooM1", properties1);
        
        Map<String, Object> properties2 = new HashMap<>();
        properties2.put("propA", "valueA2");
        properties2.put("propB", "valueB2");
        String connectorId2 = createExampleConnector("LooM2", properties2);
        
        ExampleDomain connectorProxy1 = domainFactory.getRemoteProxy(ExampleDomain.class, properties1);
        ExampleDomain connectorProxy2 = domainFactory.getRemoteProxy(ExampleDomain.class, properties2);
        
        String result1 = connectorProxy1.doSomethingWithMessage("aaa");
        assertThat(result1, CoreMatchers.equalTo(REPLY_HEADER + "LooM1: aaa"));
        
        String result2 = connectorProxy2.doSomethingWithMessage("aaa");
        assertThat(result2, CoreMatchers.equalTo(REPLY_HEADER + "LooM2: aaa"));
        
        deleteExampleConnector(connectorId1);
        deleteExampleConnector(connectorId2);
    }

    @Test
    public void testRegisterConnector() throws ConnectorValidationFailedException {
        String connectorId = registerExampleConnector();
        domainFactory.unregisterConnector(connectorId);
        domainFactory.deleteConnector(connectorId);
    }
    
    @Test
    public void testRegisterConnectorWithProperties() throws ConnectorValidationFailedException {
    	Map<String, Object> properties = new HashMap<>();
    	properties.put("prop1", "value1");
    	properties.put("prop2", "value2");
    	String connectorId = registerExampleConnector(properties);
    	domainFactory.unregisterConnector(connectorId);
    	domainFactory.deleteConnector(connectorId);
    }
    
    @Test
    public void testCallOnRegisteredConnectorWithModel() throws Exception {
        String uuid = registerExampleConnector();
        final ExampleDomain self = domainFactory.getRemoteProxy(ExampleDomain.class, uuid);
        ExampleRequestModel modelObject = new ExampleRequestModel();
        modelObject.setId(42);
        modelObject.setName("foo");
        ExampleResponseModel result = self.doSomethingWithModel(modelObject);
        assertThat(result.getResult(), is("foo"));

        domainFactory.unregisterConnector(uuid);
        domainFactory.deleteConnector(uuid);
    }

    @Test
    public void testCallOnUnregisteredConnectorFails() throws Exception {
        String uuid = registerExampleConnector();
        final ExampleDomain self = domainFactory.getRemoteProxy(ExampleDomain.class, uuid);
        assertThat(self.doSomethingWithMessage("asdf"), is("42"));
        domainFactory.unregisterConnector(uuid);
        try {
            self.doSomethingWithMessage("stuff");
            fail("expected Execution Exception, because the connector should have been unregistered");
        } catch (RemoteException e) {
            // expected
        }
        domainFactory.deleteConnector(uuid);
    }

    @Test
    public void testCallOnNotRegisteredConnectorFails() throws Exception {
        String uuid = domainFactory.createConnector("example");
        final ExampleDomain self = domainFactory.getRemoteProxy(ExampleDomain.class, uuid);
        try {
            self.doSomethingWithMessage("stuff");
            fail("expected Execution Exception, because the connector hasn't been registered");
        } catch (RemoteException e) {
            // expected
        }
        domainFactory.deleteConnector(uuid);
    }

    private String createExampleConnector(String prefix) {
    	return createExampleConnector(prefix, new HashMap<String, Object>());
    }
    
    private String createExampleConnector(String prefix, Map<String, Object> properties) {
    	ConnectorManager cm = domainFactory.getRemoteProxy(ConnectorManager.class);
        ConnectorDescription connectorDescription = new ConnectorDescription("example", "example",
            ImmutableMap.of("prefix", prefix, "level", "info"),
            properties);
        return cm.create(connectorDescription);
    }
    
    private void deleteExampleConnector(String connectorId) {
    	ConnectorManager cm = domainFactory.getRemoteProxy(ConnectorManager.class);
        cm.delete(connectorId);
    }
    
    private String registerExampleConnector() throws ConnectorValidationFailedException {
    	return registerExampleConnector(new HashMap<String, Object>());
    }
    
    private String registerExampleConnector(Map<String, Object> properties) throws ConnectorValidationFailedException {
    	ExampleDomain handler = new ExampleConnector();
        String uuid = domainFactory.createConnector("example", properties);
        domainFactory.registerConnector(uuid, handler);
        return uuid;
    }
}
