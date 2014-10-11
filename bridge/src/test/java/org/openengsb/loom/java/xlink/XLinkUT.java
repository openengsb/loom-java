package org.openengsb.loom.java.xlink;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openengsb.connector.usernamepassword.Password;
import org.openengsb.core.api.ConnectorValidationFailedException;
import org.openengsb.core.api.model.ModelDescription;
import org.openengsb.core.api.xlink.model.ModelViewMapping;
import org.openengsb.core.api.xlink.model.XLinkConnectorView;
import org.openengsb.core.api.xlink.model.XLinkObject;
import org.openengsb.core.api.xlink.service.XLinkConnectorManager;
import org.openengsb.domain.OOSourceCode.OOSourceCodeDomain;
import org.openengsb.domain.OOSourceCode.model.OOVariable;
import org.openengsb.domain.SQLCode.SQLCodeDomain;
import org.openengsb.domain.SQLCode.model.SQLCreateField;
import org.openengsb.loom.java.ProxyConnectorFactory;
import org.openengsb.loom.java.jms.JmsProtocolHandler;

public class XLinkUT {
	private static final String baseURL = "failover:(tcp://localhost:6549)?timeout=60000";
	
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
    public void testLocalXLinkSwitch() throws ConnectorValidationFailedException, ClassNotFoundException {
        XLinkConnectorManager cm = domainFactory.getRemoteProxy(XLinkConnectorManager.class);
        
        String ooConnectorId = domainFactory.createConnector("oosourcecode");
        OOSourceCodeDomain ooDomainHandler = mock(OOSourceCodeDomain.class);
        domainFactory.registerConnector(ooConnectorId, ooDomainHandler);
        cm.registerWithXLink(ooConnectorId, "127.0.0.1", "junit", initOOModelViewMappings());
        
        String sqlConnectorId = domainFactory.createConnector("sqlcode");
        SQLCodeDomain sqlDomainHandler = mock(SQLCodeDomain.class);
        domainFactory.registerConnector(sqlConnectorId, sqlDomainHandler);
        cm.registerWithXLink(sqlConnectorId, "127.0.0.1", "junit", initSqlModelViewMappings());
        
        cm.requestXLinkSwitch(ooConnectorId, "myContext", new OOVariable("y", "int", false, true), true);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<XLinkObject[]> xlinkObjectCaptor = ArgumentCaptor.forClass((Class<XLinkObject[]>)new XLinkObject[0].getClass());
        verify(ooDomainHandler).showXLinks(xlinkObjectCaptor.capture());
        assertThat(xlinkObjectCaptor.getValue().length, equalTo(2));
        
        // open sql viewer
        boolean sqlViewerFound = false;
        for (XLinkObject xLinkObject: xlinkObjectCaptor.getValue()) {
            if (SQLCreateField.class.getName().equals(xLinkObject.getModelDescription().getModelClassName())) {
                cm.openXLink(xLinkObject.getConnectorId(), xLinkObject.getModelDescription(), xLinkObject.getModelObject(), xLinkObject.getViews().get(0));
                verify(sqlDomainHandler).openXLink(any(ModelDescription.class), any(), any(XLinkConnectorView.class));
                sqlViewerFound = true;
                break;
            }
        }
        assertTrue(sqlViewerFound);
        
        cm.unregisterFromXLink(ooConnectorId);
        domainFactory.unregisterConnector(ooConnectorId);
        domainFactory.deleteConnector(ooConnectorId);
        
        cm.unregisterFromXLink(sqlConnectorId);
        domainFactory.unregisterConnector(sqlConnectorId);
        domainFactory.deleteConnector(sqlConnectorId);
    }
    
    private ModelViewMapping[] initOOModelViewMappings() {
    	 ModelViewMapping[] modelsToViews = new ModelViewMapping[1];
    	 
    	 ModelDescription ooVarModel = new ModelDescription(OOVariable.class, "3.0.0.SNAPSHOT");
         
         XLinkConnectorView[] ooViews = new XLinkConnectorView[1];
         Map<Locale, String> ooViewDescription = new HashMap<>();
         ooViewDescription.put(Locale.ENGLISH, "This view opens the values in an ObjectOrientedCode Viewer.");
         ooViewDescription.put(Locale.GERMAN, "Dieses Tool oeffnet die Werte in einem ObjectOrientedCode Viewer.");
         ooViews[0] = new XLinkConnectorView("ooViewer",
                 "ObjectOrientedCode Viewer", ooViewDescription);
         
         modelsToViews[0] = new ModelViewMapping(ooVarModel, ooViews);
         return modelsToViews;
    }
    
    private ModelViewMapping[] initSqlModelViewMappings() {
        ModelViewMapping[] modelsToViews = new ModelViewMapping[1];
        
        ModelDescription sqlFieldModel = new ModelDescription(SQLCreateField.class, "3.0.0.SNAPSHOT");
        
        XLinkConnectorView[] sqlViews = new XLinkConnectorView[1];
        Map<Locale, String> sqlViewDescription = new HashMap<>();
        sqlViewDescription.put(Locale.ENGLISH, "This view opens the values in a SQL Viewer.");
        sqlViewDescription.put(Locale.GERMAN, "Dieses Tool oeffnet die Werte in einem SQL Viewer.");
        sqlViews[0] = new XLinkConnectorView("sqlViewer",
                "SQL Code Viewer", sqlViewDescription);
        
        modelsToViews[0] = new ModelViewMapping(sqlFieldModel, sqlViews);
        return modelsToViews;
   }
}
