package io.tackle.operator;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import java.util.List;

import static io.tackle.operator.Utils.LABEL_NAME;
import static io.tackle.operator.Utils.applyDefaultMetadata;
import static io.tackle.operator.Utils.metadataName;

@ApplicationScoped
public class RestDeployer {

    private static final String RESOURCE_NAME_SUFFIX = "rest"; 
    private final Logger log = Logger.getLogger(getClass());

    public void createOrUpdateResource(KubernetesClient kubernetesClient, String namespace, String microserviceName, String image) {
        String name = metadataName(microserviceName, RESOURCE_NAME_SUFFIX);
        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        Deployment deployment = kubernetesClient.apps().deployments().load(getClass().getResourceAsStream("templates/rest-deployment.yaml")).get();
        applyDefaultMetadata(deployment, name, namespace);
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
        envs.get(1).getValueFrom().getSecretKeyRef().setName(metadataName(microserviceName, PostgreSQLDeployer.RESOURCE_NAME_SUFFIX));
        envs.get(2).getValueFrom().getSecretKeyRef().setName(metadataName(microserviceName, PostgreSQLDeployer.RESOURCE_NAME_SUFFIX));
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
                .setPath(String.format("/%s/q/health/live", microserviceName));
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getReadinessProbe()
                .getHttpGet()
                .setPath(String.format("/%s/q/health/ready", microserviceName));

        Service service = kubernetesClient.services().load(getClass().getResourceAsStream("templates/rest-service.yaml")).get();
        applyDefaultMetadata(service, name, namespace);
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