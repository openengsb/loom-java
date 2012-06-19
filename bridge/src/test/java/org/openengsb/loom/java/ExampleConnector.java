package org.openengsb.loom.java;

import org.openengsb.core.api.AliveState;
import org.openengsb.core.api.Connector;
import org.openengsb.core.common.util.ModelUtils;
import org.openengsb.domain.example.ExampleDomain;
import org.openengsb.domain.example.event.LogEvent;
import org.openengsb.domain.example.model.ExampleRequestModel;
import org.openengsb.domain.example.model.ExampleResponseModel;

public class ExampleConnector implements ExampleDomain, Connector {
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
        ExampleResponseModel result = ModelUtils.createEmptyModelObject(ExampleResponseModel.class);
        result.setResult(arg0.getName());
        return result;
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
    public String getConnectorId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getDomainId() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setConnectorId(String arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setDomainId(String arg0) {
        // TODO Auto-generated method stub

    }
}
