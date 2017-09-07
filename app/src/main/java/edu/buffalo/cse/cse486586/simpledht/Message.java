package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by omkar on 4/11/17.
 */

public class Message {
    String hashedKey;
    String text;

    public Message(String hashedKey, String text) {
        this.hashedKey = hashedKey;
        this.text = text;
    }

    public Message() {
    }

    public String getHashedKey() {
        return hashedKey;
    }

    public void setHashedKey(String hashedKey) {
        this.hashedKey = hashedKey;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
