package io.tackle.operator;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@Group("tackle.io")
@Version("v1alpha1")
// or Void for specs as well letting the specific component (REST, DB) to know the version?
public class Microservice extends CustomResource<MicroserviceSpec, Void> implements Namespaced {}
