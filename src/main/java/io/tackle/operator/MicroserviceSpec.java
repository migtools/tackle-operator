package io.tackle.operator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class MicroserviceSpec {

    private String restImage;
    private String postgreSQLImage;
    private String oidcAuthServerUrl;
    private String databaseSchema;
    private String contextRoot;

    public String getRestImage() {
        return restImage;
    }

    public void setRestImage(String restImage) {
        this.restImage = restImage;
    }

    public String getPostgreSQLImage() {
        return postgreSQLImage;
    }

    public void setPostgreSQLImage(String postgreSQLImage) {
        this.postgreSQLImage = postgreSQLImage;
    }

    public String getOidcAuthServerUrl() {
        return oidcAuthServerUrl;
    }

    public void setOidcAuthServerUrl(String oidcAuthServerUrl) {
        this.oidcAuthServerUrl = oidcAuthServerUrl;
    }

    public String getDatabaseSchema() {
        return databaseSchema;
    }

    public void setDatabaseSchema(String databaseSchema) {
        this.databaseSchema = databaseSchema;
    }

    public String getContextRoot() {
        return contextRoot;
    }

    public void setContextRoot(String contextRoot) {
        this.contextRoot = contextRoot;
    }
}
