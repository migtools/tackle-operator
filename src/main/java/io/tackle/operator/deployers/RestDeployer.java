package io.tackle.operator.deployers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.tackle.operator.Tackle;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

import static io.tackle.operator.Utils.LABEL_NAME;
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
                                       String image,
                                       String oidcAuthServerUrl,
                                       String contextRoot,
                                       String postgreSQLName,
                                       String postgreSQLSchema) {
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
        List<EnvVar> envs =deployment
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
