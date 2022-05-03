/*
 * Copyright © 2021 the Konveyor Contributors (https://konveyor.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tackle.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretList;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Utils {

    public static final String LABEL_NAME = "app.kubernetes.io/name"; 
    public static final String LABEL_INSTANCE = "app.kubernetes.io/instance";
    public static final String DOCKERHUB_IMAGE_PULLER_SECRET_NAME = "docker-hub-image-puller";
    public static final String CONDITION_STATUS_TRUE = "True";
    public static final String CONDITION_TYPE_READY = "Ready";

    public static <P extends CustomResource, C extends HasMetadata & Namespaced> void applyDefaultMetadata(P parent, C child) {
        applyDefaultMetadata(parent, child, null);
    }

    public static <P extends CustomResource, C extends HasMetadata & Namespaced> void applyDefaultMetadata(P parent, C child, String suffix) {
        applyDefaultMetadata(child, metadataName(parent, suffix), parent.getMetadata().getNamespace());
        child.getMetadata().getOwnerReferences().addAll(parent.getMetadata().getOwnerReferences());
        child.getMetadata().getOwnerReferences().add(buildOwnerReference(parent));
    }

    public static <P extends CustomResource, C extends HasMetadata & Namespaced> void applyDefaultMetadata(Tackle tackle, String creatorName, C child, String suffix) {
        applyDefaultMetadata(child, metadataName(creatorName, suffix), tackle.getMetadata().getNamespace());
        child.getMetadata().getOwnerReferences().add(buildOwnerReference(tackle));
    }

    private static <C extends HasMetadata & Namespaced> void applyDefaultMetadata(C customResource, String name, String namespace) {
        customResource.getMetadata().setName(name);
        customResource.getMetadata().setNamespace(namespace);
        customResource.getMetadata().getLabels().put(LABEL_NAME, name);
        customResource.getMetadata().getLabels().put(LABEL_INSTANCE, String.format("%s-%d", name, ThreadLocalRandom.current().nextInt(0, 101)));
    }

    private static <S, T> OwnerReference buildOwnerReference(CustomResource<S, T> customResource) {
        return new OwnerReferenceBuilder()
                .withApiVersion(customResource.getApiVersion())
                .withKind(customResource.getKind())
                .withName(customResource.getMetadata().getName())
                .withUid(customResource.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .build();
    }

    public static <C extends HasMetadata> String metadataName(C customResource) {
        return metadataName(customResource, null);
    }

    public static <C extends HasMetadata> String metadataName(C customResource, String suffix) {
        return metadataName(customResource.getMetadata().getName(), suffix);
    }

    public static String metadataName(String customResourceName, String suffix) {
        return suffix == null ? customResourceName : String.format("%s-%s", customResourceName, suffix);
    }

    public static void addDockerhubImagePullSecret(Deployment deployment, NonNamespaceOperation<Secret, SecretList, Resource<Secret>> secrets) {
        if (secrets.withName(DOCKERHUB_IMAGE_PULLER_SECRET_NAME).get() != null) {
            deployment.getSpec().getTemplate().getSpec().getImagePullSecrets().add(new LocalObjectReference(DOCKERHUB_IMAGE_PULLER_SECRET_NAME));
        }
    }

    public static void addOpenshiftAnnotationConnectsTo(HasMetadata resource, String... connections) {
        addOpenshiftAnnotationConnectsTo(resource, Arrays.asList(connections));
    }

    public static void addOpenshiftAnnotationConnectsTo(HasMetadata resource, List<String> connections) {
        final List<String> connectsTo = connections.stream()
                .map(connection -> String.format("{\"apiVersion\":\"apps/v1\",\"kind\":\"Deployment\",\"name\":\"%s\"}", connection))
                .collect(Collectors.toList());
        final ObjectMeta metadata = resource.getMetadata();
        Map<String, String> annotations = Optional.ofNullable(metadata.getAnnotations())
                .orElseGet(() -> {
                    Map<String, String> newAnnotations = new HashMap<>();
                    metadata.setAnnotations(newAnnotations);
                    return newAnnotations;
                });
        annotations.put("app.openshift.io/connects-to", connectsTo.toString());
    }
}
