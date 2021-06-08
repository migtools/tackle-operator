package io.tackle.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.util.concurrent.ThreadLocalRandom;

public class Utils {

    public static final String LABEL_NAME = "app.kubernetes.io/name"; 
    public static final String LABEL_INSTANCE = "app.kubernetes.io/instance";
    public static final String DOCKERHUB_IMAGE_PULLER_SECRET_NAME = "docker-hub-image-puller";

    public static <R extends HasMetadata & Namespaced> void applyDefaultMetadata(R resource, String name, String namespace) {
        resource.getMetadata().setName(name);
        resource.getMetadata().setNamespace(namespace);
        resource.getMetadata().getLabels().put(LABEL_NAME, name);
        resource.getMetadata().getLabels().put(LABEL_INSTANCE, String.format("%s-%d", name, ThreadLocalRandom.current().nextInt(0, 101)));
    }

    public static <S, T> String metadataName(CustomResource<S, T> customResource) {
        return customResource.getMetadata().getName();
    }

    public static <S, T> String metadataName(CustomResource<S, T> customResource, String suffix) {
        return String.format("%s-%s", metadataName(customResource), suffix);
    }

    public static <S, T> String metadataName(String microserviceName, String suffix) {
        return String.format("%s-%s", microserviceName, suffix);
    }

    public static void addDockerhubImagePullSecret(Deployment deployment, NonNamespaceOperation<Secret, SecretList, Resource<Secret>> secrets) {
        if (secrets.withName(DOCKERHUB_IMAGE_PULLER_SECRET_NAME).get() != null) {
            deployment.getSpec().getTemplate().getSpec().getImagePullSecrets().add(new LocalObjectReference(DOCKERHUB_IMAGE_PULLER_SECRET_NAME));
        }
    }
}
