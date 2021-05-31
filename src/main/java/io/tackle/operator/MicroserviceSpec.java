package io.tackle.operator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class MicroserviceSpec {

    private String rest;
    private String postgreSQL;

    public String getPostgreSQL() {
        return postgreSQL;
    }

    public void setPostgreSQL(String postgreSQL) {
        this.postgreSQL = postgreSQL;
    }

    public String getRest() {
        return rest;
    }

    public void setRest(String rest) {
        this.rest = rest;
    }
}
