package io.tackle.operator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class BasicStatus {
    
    private String status;
    private Integer readyReplicas = 0;

    public String getStatus() {
        return status;
    }

    public Integer getReadyReplicas() {
        return readyReplicas;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public void setReadyReplicas(Integer readyReplicas) {
        this.readyReplicas = readyReplicas;
    }
}
