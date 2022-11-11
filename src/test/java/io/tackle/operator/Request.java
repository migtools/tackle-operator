package io.tackle.operator;

public class Request {
    String path;
    String method;
    String body;

    public Request(String path, String method, String body) {
        this.path = path;
        this.method = method;
        this.body = body;
    }
}
