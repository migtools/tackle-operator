package io.tackle.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.jboss.logging.Logger;

import javax.inject.Inject;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class MicroserviceController extends AbstractController implements ResourceController<Microservice> {

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
        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        log.infof("Creating or updating PostgreSQL for Microservice '%s' in namespace '%s'", name, namespace);
        postgreSQLDeployer.createOrUpdateResource(kubernetesClient, namespace, name, microservice.getSpec().getPostgreSQLImage());

        log.infof("Creating or updating REST for Microservice '%s' in namespace '%s'", name, namespace);
        restDeployer.createOrUpdateResource(kubernetesClient, namespace, name, microservice.getSpec().getRestImage());

        return UpdateControl.updateCustomResource(microservice);
    }

    @Override
    public DeleteControl deleteResource(Microservice microservice, Context<Microservice> context) {
        String namespace = microservice.getMetadata().getNamespace();
        String name = metadataName(microservice);

        return DeleteControl.DEFAULT_DELETE;
    }

}
