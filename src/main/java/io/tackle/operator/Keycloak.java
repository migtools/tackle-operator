package io.tackle.operator;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@Group("tackle.io")
@Version("v1alpha1")
public class Keycloak extends CustomResource<BasicSpec, BasicStatus> implements Namespaced {}
