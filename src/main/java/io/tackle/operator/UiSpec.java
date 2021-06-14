package io.tackle.operator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class UiSpec extends BasicSpec{
    private String controlsApiUrl;
    private String applicationInventoryApiUrl;
    private String pathfinderApiUrl;
    private String ssoApiUrl;

    public String getControlsApiUrl() {
        return controlsApiUrl;
    }

    public void setControlsApiUrl(String controlsApiUrl) {
        this.controlsApiUrl = controlsApiUrl;
    }

    public String getApplicationInventoryApiUrl() {
        return applicationInventoryApiUrl;
    }

    public void setApplicationInventoryApiUrl(String applicationInventoryApiUrl) {
        this.applicationInventoryApiUrl = applicationInventoryApiUrl;
    }

    public String getPathfinderApiUrl() {
        return pathfinderApiUrl;
    }

    public void setPathfinderApiUrl(String pathfinderApiUrl) {
        this.pathfinderApiUrl = pathfinderApiUrl;
    }

    public String getSsoApiUrl() {
        return ssoApiUrl;
    }

    public void setSsoApiUrl(String ssoApiUrl) {
        this.ssoApiUrl = ssoApiUrl;
    }
}
