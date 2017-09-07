package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by omkar on 4/10/17.
 */

public class Node {
    String portNumber;
    String portHashID;
    int address;

    public String getPortNumber() {
        return portNumber;
    }

    public String getPortHashID() {
        return portHashID;
    }

    Node predecessor;
    Node successor;

    public Node() {
    }

    public Node(String portNumber, String portHashID, int address) {
        this.portNumber = portNumber;
        this.portHashID = portHashID;
        this.address = address;
    }

    public void setPredecessor(Node predecessor) {
        this.predecessor = predecessor;
    }

    public void setSuccessor(Node successor) {
        this.successor = successor;
    }

    public void setPortNumber(String portNumber) {

        this.portNumber = portNumber;
    }

    public void setPortHashID(String portHashID) {
        this.portHashID = portHashID;
    }

    public void setAddress() {
        this.address = Integer.parseInt(this.portNumber)*2;
    }
}
