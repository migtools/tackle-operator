package io.tackle.operator;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.ListOptions;
import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.jboss.logging.Logger;

import javax.inject.Inject;

import static io.tackle.operator.Utils.metadataName;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class MicroserviceController implements ResourceController<Microservice> {

    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;
    @Inject
    PostgreSQLDeployer postgreSQLDeployer;
    @Inject
    RestDeployer restDeployer;

    @Override
    public UpdateControl<Microservice> createOrUpdateResource(Microservice microservice, Context<Microservice> context) {
        String namespace = microservice.getMetadata().getNamespace();
        String name = metadataName(microservice);

        MixedOperation<Microservice, KubernetesResourceList<Microservice>, Resource<Microservice>> microserviceClient = kubernetesClient.customResources(Microservice.class);
        final String restImage = microservice.getSpec().getRestImage();
        ListOptions listOptions = new ListOptionsBuilder().withFieldSelector("metadata.name!=" + name).build();
        MixedOperation<Tackle, KubernetesResourceList<Tackle>, Resource<Tackle>> tackleClient = kubernetesClient.customResources(Tackle.class);
        if (tackleClient.inNamespace(namespace).list().getItems().isEmpty() ||
                microservice.getMetadata().getOwnerReferences().isEmpty()) {
            log.errorf("Standalone '%s' Microservice CR isn't allowed: create a Tackle CR to instantiate Tackle application", name);
            microserviceClient.delete(microservice);
            return UpdateControl.noUpdate();
        } else if (microserviceClient
                .inNamespace(namespace)
                .list(listOptions)
                .getItems()
                .stream()
                .anyMatch(microserviceAlreadyDeployed -> restImage.equals(microserviceAlreadyDeployed.getSpec().getRestImage()) &&
                        microservice.getMetadata().getOwnerReferences().containsAll(microserviceAlreadyDeployed.getMetadata().getOwnerReferences()))) {
            log.warnf("Only one Microservice CR running %s image is allowed: '%s' is going to be deleted", restImage, name);
            microserviceClient.delete(microservice);
            return UpdateControl.noUpdate();
        }

        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);
        log.infof("Creating or updating PostgreSQL for Microservice '%s' in namespace '%s'", name, namespace);
        postgreSQLDeployer.createOrUpdateResource(kubernetesClient, microservice);

        log.infof("Creating or updating REST for Microservice '%s' in namespace '%s'", name, namespace);
        restDeployer.createOrUpdateResource(kubernetesClient, microservice);

        return UpdateControl.updateCustomResource(microservice);
    }

    @Override
    public DeleteControl deleteResource(Microservice microservice, Context<Microservice> context) {
        String namespace = microservice.getMetadata().getNamespace();
        String name = metadataName(microservice);
        log.infof("Execution deleteResource for '%s' in namespace '%s'", name, namespace);
        return DeleteControl.DEFAULT_DELETE;
    }

}
