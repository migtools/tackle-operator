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

    public static <P extends CustomResource, C extends HasMetadata & Namespaced> void applyDefaultMetadata(P parent, C child) {
        applyDefaultMetadata(parent, child, null);
    }

    public static <P extends CustomResource, C extends HasMetadata & Namespaced> void applyDefaultMetadata(P parent, C child, String suffix) {
        final String name = metadataName(parent, suffix);
        final String namespace = parent.getMetadata().getNamespace();
        child.getMetadata().setName(name);
        child.getMetadata().setNamespace(namespace);
        child.getMetadata().getLabels().put(LABEL_NAME, name);
        child.getMetadata().getLabels().put(LABEL_INSTANCE, String.format("%s-%d", name, ThreadLocalRandom.current().nextInt(0, 101)));
        child.getMetadata().getOwnerReferences().addAll(parent.getMetadata().getOwnerReferences());
    }

    public static <C extends HasMetadata> String metadataName(C customResource) {
        return metadataName(customResource, null);
    }

    public static <C extends HasMetadata> String metadataName(C customResource, String suffix) {
        final String name = customResource.getMetadata().getName();
        return suffix == null ? name : String.format("%s-%s", name, suffix);
    }

    public static void addDockerhubImagePullSecret(Deployment deployment, NonNamespaceOperation<Secret, SecretList, Resource<Secret>> secrets) {
        if (secrets.withName(DOCKERHUB_IMAGE_PULLER_SECRET_NAME).get() != null) {
            deployment.getSpec().getTemplate().getSpec().getImagePullSecrets().add(new LocalObjectReference(DOCKERHUB_IMAGE_PULLER_SECRET_NAME));
        }
    }
}
