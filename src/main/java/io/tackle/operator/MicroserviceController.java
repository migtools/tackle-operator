package io.tackle.operator;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
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

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class MicroserviceController extends AbstractController implements ResourceController<Microservice> {

    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<Microservice> createOrUpdateResource(Microservice microservice, Context<Microservice> context) {
        String namespace = microservice.getMetadata().getNamespace();
        String name = metadataName(microservice);
        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        MixedOperation<PostgreSQL, KubernetesResourceList<PostgreSQL>, Resource<PostgreSQL>> postgreSQLClient = kubernetesClient.customResources(PostgreSQL.class);
        final String postgresqlCR = microservice.getSpec().getPostgreSQL();
        PostgreSQL postgreSQL = postgreSQLClient.load(TackleController.class.getResourceAsStream(postgresqlCR)).get();

        MixedOperation<Rest, KubernetesResourceList<Rest>, Resource<Rest>> restClient = kubernetesClient.customResources(Rest.class);
        final String restCR = microservice.getSpec().getRest();
        Rest rest = restClient.load(TackleController.class.getResourceAsStream(restCR)).get();

        log.infof("Creating or updating PostgreSQL '%s' in namespace '%s'", postgreSQL.getMetadata().getName(), namespace);
        postgreSQLClient.inNamespace(namespace).createOrReplace(postgreSQL);

        log.infof("Creating or updating REST '%s' in namespace '%s'", rest.getMetadata().getName(), namespace);
        restClient.inNamespace(namespace).createOrReplace(rest);

        return UpdateControl.updateCustomResource(microservice);
    }

    @Override
    public DeleteControl deleteResource(Microservice microservice, Context<Microservice> context) {
        String namespace = microservice.getMetadata().getNamespace();
        String name = metadataName(microservice);

        return DeleteControl.DEFAULT_DELETE;
    }

}
