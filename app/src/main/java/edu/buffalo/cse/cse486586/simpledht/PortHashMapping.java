package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by omkar on 4/13/17.
 */

public class PortHashMapping implements Comparable<PortHashMapping>{
    String portNumber;
    String portHash;
    boolean lastElement = false;

    public PortHashMapping(String portNumber, String portHash) {
        this.portNumber = portNumber;
        this.portHash = portHash;
    }

    public String getPortHash() {
        return portHash;
    }

    @Override
    public int compareTo(PortHashMapping portHashMapping) {
        return this.portHash.compareTo(portHashMapping.getPortHash());
    }

    public String getPortNumber() {
        return portNumber;
    }

    public PortHashMapping() {

    }

    public void setLastElement(boolean lastElement) {

        this.lastElement = lastElement;
    }
}
