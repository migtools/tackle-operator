package io.tackle.operator;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.util.List;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class RestController extends AbstractController implements ResourceController<Rest> {

    private static final String RESOURCE_NAME_SUFFIX = "rest"; 
    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<Rest> createOrUpdateResource(Rest rest, Context<Rest> context) {
        String namespace = rest.getMetadata().getNamespace();
        String name = metadataName(rest, RESOURCE_NAME_SUFFIX);
        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        Deployment deployment = loadYaml(Deployment.class, "templates/rest-deployment.yaml");
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
        envs.get(1).getValueFrom().getSecretKeyRef().setName(metadataName(rest, PostgreSQLController.RESOURCE_NAME_SUFFIX));
        envs.get(2).getValueFrom().getSecretKeyRef().setName(metadataName(rest, PostgreSQLController.RESOURCE_NAME_SUFFIX));
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setImage(rest.getSpec().getImage());
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
                .setPath(String.format("/%s/q/health/live", metadataName(rest)));
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getReadinessProbe()
                .getHttpGet()
                .setPath(String.format("/%s/q/health/ready", metadataName(rest)));

        Service service = loadYaml(Service.class, "templates/rest-service.yaml");
        applyDefaultMetadata(service, name, namespace);
        service
                .getSpec()
                .getSelector()
                .put(LABEL_NAME, name);

        log.infof("Creating or updating Deployment '%s' in namespace '%s'", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        log.infof("Creating or updating Service '%s' in namespace '%s'", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);

        BasicStatus status = new BasicStatus();
        rest.setStatus(status);
        return UpdateControl.updateCustomResource(rest);
    }

    @Override
    public DeleteControl deleteResource(Rest rest, Context<Rest> context) {
        String namespace = rest.getMetadata().getNamespace();
        String name = metadataName(rest, RESOURCE_NAME_SUFFIX);
        log.infof("Execution deleteResource for '%s' in namespace '%s'", name, namespace);

        log.infof("Deleting Service '%s' in namespace '%s'", name, namespace);
        ServiceResource<Service> service =
                kubernetesClient
                        .services()
                        .inNamespace(namespace)
                        .withName(name);
        if (service.get() != null) {
            service.delete();
        }
        log.infof("Deleted Service '%s' in namespace '%s'", name, namespace);

        log.infof("Deleting Deployment '%s' in namespace '%s'", name, namespace);
        RollableScalableResource<Deployment> deployment =
                kubernetesClient
                        .apps()
                        .deployments()
                        .inNamespace(namespace)
                        .withName(name);
        if (deployment.get() != null) {
            deployment.withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
        }
        log.infof("Deleted Deployment '%s' in namespace '%s' with propagation", name, namespace);

        return DeleteControl.DEFAULT_DELETE;
    }

}
