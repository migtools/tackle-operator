package io.tackle.operator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TackleSpec {

    private String dockerhubConfigJson;

    public String getDockerhubConfigJson() {
        return dockerhubConfigJson;
    }

    public void setDockerhubConfigJson(String dockerhubConfigJson) {
        this.dockerhubConfigJson = dockerhubConfigJson;
    }
}
