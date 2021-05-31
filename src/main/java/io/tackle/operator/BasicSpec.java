package io.tackle.operator;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class BasicSpec {

    private String image;

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
