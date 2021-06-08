package io.tackle.operator;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class TackleController implements ResourceController<Tackle> {

    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public DeleteControl deleteResource(Tackle tackle, Context<Tackle> context) {
        String namespace = tackle.getMetadata().getNamespace();
        kubernetesClient.customResources(Microservice.class).inNamespace(namespace).delete(kubernetesClient.customResources(Microservice.class).inNamespace(namespace).list().getItems());
        kubernetesClient.customResources(Keycloak.class).inNamespace(namespace).delete(kubernetesClient.customResources(Keycloak.class).inNamespace(namespace).list().getItems());
        kubernetesClient.customResources(Ui.class).inNamespace(namespace).delete(kubernetesClient.customResources(Ui.class).inNamespace(namespace).list().getItems());
        Resource<Secret> dockerhubSecret = kubernetesClient.secrets().inNamespace(namespace).withName(AbstractController.DOCKERHUB_IMAGE_PULLER_SECRET_NAME);
        if (dockerhubSecret.get() != null) {
            dockerhubSecret.delete();
            log.infof("Deleted Secret '%s' in namespace '%s'", AbstractController.DOCKERHUB_IMAGE_PULLER_SECRET_NAME, namespace);
        }
        return DeleteControl.DEFAULT_DELETE;
    }

    @Override
    public UpdateControl<Tackle> createOrUpdateResource(Tackle tackle, Context<Tackle> context) {
        String namespace = tackle.getMetadata().getNamespace();

        final TackleSpec tackleSpec = tackle.getSpec();
        if (tackleSpec != null) {
            final String dockerhubConfigJson = tackleSpec.getDockerhubConfigJson();
            if (!StringUtils.isEmpty(dockerhubConfigJson)) {
                Secret dockerhubSecret = kubernetesClient.secrets().load(getClass().getResourceAsStream("templates/docker-hub-image-puller.yaml")).get();
                dockerhubSecret.getMetadata().setNamespace(namespace);
                Map<String, String> data = Collections.singletonMap(".dockerconfigjson", dockerhubConfigJson);
                dockerhubSecret.setData(data);
                kubernetesClient.secrets().inNamespace(namespace).createOrReplace(dockerhubSecret);
            }
            else log.warn("No 'spec.dockerhubConfigJson' has been provided: anonymous image pulling from Dockerhub could suffer for rate limits.");
        }
        else log.info("Tackle resource without spec: default configuration will be applied");

        // deploy Keycloak instance
        MixedOperation<Keycloak, KubernetesResourceList<Keycloak>, Resource<Keycloak>> keycloakClient = kubernetesClient.customResources(Keycloak.class);
        Keycloak keycloak = keycloakClient.load(TackleController.class.getResourceAsStream("keycloak/keycloak.yaml")).get();
        keycloakClient.inNamespace(namespace).createOrReplace(keycloak);

        // deploy microservices
        MixedOperation<Microservice, KubernetesResourceList<Microservice>, Resource<Microservice>> microserviceClient = kubernetesClient.customResources(Microservice.class);

        Microservice applicationInventory = microserviceClient.load(TackleController.class.getResourceAsStream("microservice/tackle-application-inventory.yaml")).get();
        microserviceClient.inNamespace(namespace).createOrReplace(applicationInventory);

        Microservice controls = microserviceClient.load(TackleController.class.getResourceAsStream("microservice/tackle-controls.yaml")).get();
        microserviceClient.inNamespace(namespace).createOrReplace(controls);

        Microservice pathfinder = microserviceClient.load(TackleController.class.getResourceAsStream("microservice/tackle-pathfinder.yaml")).get();
        microserviceClient.inNamespace(namespace).createOrReplace(pathfinder);

        // deploy the UI instance
        MixedOperation<Ui, KubernetesResourceList<Ui>, Resource<Ui>> uiClient = kubernetesClient.customResources(Ui.class);
        Ui ui = uiClient.load(TackleController.class.getResourceAsStream("ui/tackle-ui.yaml")).get();
        uiClient.inNamespace(namespace).createOrReplace(ui);

        BasicStatus status = new BasicStatus();
        tackle.setStatus(status);
        return UpdateControl.updateCustomResource(tackle);
    }

}
