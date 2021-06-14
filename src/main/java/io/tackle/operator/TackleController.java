package io.tackle.operator;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

import static io.tackle.operator.Utils.CONDITION_STATUS_TRUE;
import static io.tackle.operator.Utils.CONDITION_TYPE_READY;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class TackleController implements ResourceController<Tackle> {

    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public DeleteControl deleteResource(Tackle tackle, Context<Tackle> context) {
        final String namespace = tackle.getMetadata().getNamespace();
        final String name = tackle.getMetadata().getName();
        log.infof("Execution deleteResource for Tackle '%s' in namespace '%s'", name, namespace);
        return DeleteControl.DEFAULT_DELETE;
    }

    @Override
    public UpdateControl<Tackle> createOrUpdateResource(Tackle tackle, Context<Tackle> context) {
        final String namespace = tackle.getMetadata().getNamespace();
        final String name = tackle.getMetadata().getName();

        BasicStatus status = tackle.getStatus();
        if (status != null && status.getConditions()
                .stream()
                .anyMatch(condition ->
                        CONDITION_TYPE_READY.equals(condition.getType()) &&
                        CONDITION_STATUS_TRUE.equals(condition.getStatus()))) {
                log.infof("Tackle '%s' CR already created, nothing to do.", tackle.getMetadata().getName());
                return UpdateControl.noUpdate();
        }

        final OwnerReference tackleOwnerReference = new OwnerReferenceBuilder()
                .withApiVersion(tackle.getApiVersion())
                .withKind(tackle.getKind())
                .withName(tackle.getMetadata().getName())
                .withUid(tackle.getMetadata().getUid())
                .withBlockOwnerDeletion(true)
                .build();

        final TackleSpec tackleSpec = tackle.getSpec();
        if (tackleSpec != null) {
            final String dockerhubConfigJson = tackleSpec.getDockerhubConfigJson();
            if (!StringUtils.isEmpty(dockerhubConfigJson)) {
                Secret dockerhubSecret = kubernetesClient.secrets().load(getClass().getResourceAsStream("templates/docker-hub-image-puller.yaml")).get();
                dockerhubSecret.getMetadata().setNamespace(namespace);
                Map<String, String> data = Collections.singletonMap(".dockerconfigjson", dockerhubConfigJson);
                dockerhubSecret.setData(data);
                dockerhubSecret.getMetadata().getOwnerReferences().add(tackleOwnerReference);
                kubernetesClient.secrets().inNamespace(namespace).createOrReplace(dockerhubSecret);
            }
            else log.warn("No 'spec.dockerhubConfigJson' has been provided: anonymous image pulling from Dockerhub could suffer for rate limits.");
        }
        else log.info("Tackle resource without spec: default configuration will be applied");

        // deploy Keycloak instance
        MixedOperation<Keycloak, KubernetesResourceList<Keycloak>, Resource<Keycloak>> keycloakClient = kubernetesClient.customResources(Keycloak.class);
        Keycloak keycloak = keycloakClient.load(TackleController.class.getResourceAsStream("keycloak/keycloak.yaml")).get();
        keycloak.getMetadata().getOwnerReferences().add(tackleOwnerReference);
        keycloak.getMetadata().setName(String.format("%s-%s", name, keycloak.getMetadata().getName()));
        keycloakClient.inNamespace(namespace).createOrReplace(keycloak);

        // deploy microservices
        MixedOperation<Microservice, KubernetesResourceList<Microservice>, Resource<Microservice>> microserviceClient = kubernetesClient.customResources(Microservice.class);

        Microservice applicationInventory = microserviceClient.load(TackleController.class.getResourceAsStream("microservice/tackle-application-inventory.yaml")).get();
        applicationInventory.getMetadata().getOwnerReferences().add(tackleOwnerReference);
        applicationInventory.getSpec().setOidcAuthServerUrl(String.format("http://%s:8080/auth/realms/quarkus", keycloak.getMetadata().getName()));
        applicationInventory.getMetadata().setName(String.format("%s-%s", name, applicationInventory.getMetadata().getName()));
        microserviceClient.inNamespace(namespace).createOrReplace(applicationInventory);

        Microservice controls = microserviceClient.load(TackleController.class.getResourceAsStream("microservice/tackle-controls.yaml")).get();
        controls.getMetadata().getOwnerReferences().add(tackleOwnerReference);
        controls.getSpec().setOidcAuthServerUrl(String.format("http://%s:8080/auth/realms/quarkus", keycloak.getMetadata().getName()));
        controls.getMetadata().setName(String.format("%s-%s", name, controls.getMetadata().getName()));
        microserviceClient.inNamespace(namespace).createOrReplace(controls);

        Microservice pathfinder = microserviceClient.load(TackleController.class.getResourceAsStream("microservice/tackle-pathfinder.yaml")).get();
        pathfinder.getMetadata().getOwnerReferences().add(tackleOwnerReference);
        pathfinder.getSpec().setOidcAuthServerUrl(String.format("http://%s:8080/auth/realms/quarkus", keycloak.getMetadata().getName()));
        pathfinder.getMetadata().setName(String.format("%s-%s", name, pathfinder.getMetadata().getName()));
        microserviceClient.inNamespace(namespace).createOrReplace(pathfinder);

        // deploy the UI instance
        MixedOperation<Ui, KubernetesResourceList<Ui>, Resource<Ui>> uiClient = kubernetesClient.customResources(Ui.class);
        Ui ui = uiClient.load(TackleController.class.getResourceAsStream("ui/tackle-ui.yaml")).get();
        ui.getMetadata().getOwnerReferences().add(tackleOwnerReference);
        ui.getSpec().setControlsApiUrl(String.format("http://%s-rest:8080", controls.getMetadata().getName()));
        ui.getSpec().setApplicationInventoryApiUrl(String.format("http://%s-rest:8080", applicationInventory.getMetadata().getName()));
        ui.getSpec().setPathfinderApiUrl(String.format("http://%s-rest:8080", pathfinder.getMetadata().getName()));
        ui.getSpec().setSsoApiUrl(String.format("http://%s:8080", keycloak.getMetadata().getName()));
        ui.getMetadata().setName(String.format("%s-%s", name, ui.getMetadata().getName()));
        uiClient.inNamespace(namespace).createOrReplace(ui);

        if (status == null) {
            status = new BasicStatus();
            tackle.setStatus(status);
        }
        Condition condition = new ConditionBuilder()
                .withLastTransitionTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .withType(CONDITION_TYPE_READY)
                .withStatus(CONDITION_STATUS_TRUE)
                .withReason("-")
                .withMessage("-")
                .build();
        status.addCondition(condition);
        return UpdateControl.updateStatusSubResource(tackle);
    }

}
