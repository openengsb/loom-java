package org.openengsb.loom.java;

public class RemoteException extends RuntimeException {

    private static final long serialVersionUID = -2754898107429058007L;

    public RemoteException() {
        super();
    }

    public RemoteException(String message, Throwable cause) {
        super(message, cause);
    }

    public RemoteException(String message) {
        super(message);
    }

    public RemoteException(Throwable cause) {
        super(cause);
    }

}
