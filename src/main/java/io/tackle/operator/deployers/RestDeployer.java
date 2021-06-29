package io.tackle.operator.deployers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.tackle.operator.Tackle;
import io.tackle.operator.TackleController;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static io.tackle.operator.Utils.LABEL_NAME;
import static io.tackle.operator.Utils.addOpenshiftAnnotationConnectsTo;
import static io.tackle.operator.Utils.applyDefaultMetadata;
import static io.tackle.operator.Utils.metadataName;

@ApplicationScoped
public class RestDeployer {

    private static final String RESOURCE_NAME_SUFFIX = "rest";
    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    public void createOrUpdateResource(Tackle tackle,
                                       String creatorName,
                                       String microserviceSuffix,
                                       String image,
                                       String oidcAuthServerUrl,
                                       String contextRoot,
                                       String postgreSQLName,
                                       String postgreSQLSchema,
                                       List<String> annotationConnectsTo,
                                       Map<String, String> microservicesDeployed) {
        final String namespace = tackle.getMetadata().getNamespace();
        final String name = metadataName(creatorName, RESOURCE_NAME_SUFFIX);
        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        Deployment deployment = kubernetesClient.apps().deployments().load(getClass().getResourceAsStream("templates/rest-deployment.yaml")).get();
        applyDefaultMetadata(tackle, creatorName, deployment, RESOURCE_NAME_SUFFIX);
        deployment
                .getSpec()
                .getSelector()
                .getMatchLabels()
                .put(LABEL_NAME, name);
        deployment
                .getSpec()
                .getTemplate()
                .getMetadata()
                .getLabels()
                .put(LABEL_NAME, name);
        List<EnvVar> envs = deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv();
        // env are positional in the provided yaml deployment
        envs.get(1).setValue(String.format("jdbc:postgresql://%s:5432/%s", postgreSQLName, postgreSQLSchema));
        envs.get(2).getValueFrom().getSecretKeyRef().setName(postgreSQLName);
        envs.get(3).getValueFrom().getSecretKeyRef().setName(postgreSQLName);
        envs.get(4).setValue(oidcAuthServerUrl);
        if (TackleController.APPLICATION_INVENTORY.equals(microserviceSuffix)) {
            envs.add(5, new EnvVarBuilder()
                    .withName("IO_TACKLE_APPLICATIONINVENTORY_SERVICES_CONTROLS_SERVICE")
                    .withValue(String.format("%s-%s:8080", microservicesDeployed.get(TackleController.CONTROLS), RESOURCE_NAME_SUFFIX))
                    .build());
        }
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setImage(image);
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setName(name);
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getLivenessProbe()
                .getHttpGet()
                .setPath(String.format("/%s/q/health/live", contextRoot));
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getReadinessProbe()
                .getHttpGet()
                .setPath(String.format("/%s/q/health/ready", contextRoot));
        addOpenshiftAnnotationConnectsTo(deployment, annotationConnectsTo);

        Service service = kubernetesClient.services().load(getClass().getResourceAsStream("templates/rest-service.yaml")).get();
        applyDefaultMetadata(tackle, creatorName, service, RESOURCE_NAME_SUFFIX);
        service
                .getSpec()
                .getSelector()
                .put(LABEL_NAME, name);

        log.infof("Creating or updating Deployment '%s' in namespace '%s'", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        log.infof("Creating or updating Service '%s' in namespace '%s'", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);
    }

}
