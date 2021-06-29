package io.tackle.operator;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.tackle.operator.deployers.KeycloakDeployer;
import io.tackle.operator.deployers.MicroserviceDeployer;
import io.tackle.operator.deployers.UiDeployer;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.tackle.operator.Utils.CONDITION_STATUS_TRUE;
import static io.tackle.operator.Utils.CONDITION_TYPE_READY;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class TackleController implements ResourceController<Tackle> {

    private final Logger log = Logger.getLogger(getClass());
    public static final String KEYCLOAK = "keycloak";
    public static final String CONTROLS = "controls";
    public static final String APPLICATION_INVENTORY = "application-inventory";
    public static final String PATHFINDER = "pathfinder";

    @Inject
    KubernetesClient kubernetesClient;
    @Inject
    UiDeployer uiDeployer;
    @Inject
    KeycloakDeployer keycloakDeployer;
    @Inject
    MicroserviceDeployer microserviceDeployer;

    @ConfigProperty(name = "io.tackle.operator.keycloak.image")
    String keycloakImage;
    @ConfigProperty(name = "io.tackle.operator.keycloak.db.image")
    String keycloakDbImage;
    @ConfigProperty(name = "io.tackle.operator.controls.image")
    String controlsImage;
    @ConfigProperty(name = "io.tackle.operator.controls.db.image")
    String controlsDbImage;
    @ConfigProperty(name = "io.tackle.operator.pathfinder.image")
    String pathfinderImage;
    @ConfigProperty(name = "io.tackle.operator.pathfinder.db.image")
    String pathfinderDbImage;
    @ConfigProperty(name = "io.tackle.operator.application-inventory.image")
    String applicationInventoryImage;
    @ConfigProperty(name = "io.tackle.operator.application-inventory.db.image")
    String applicationInventoryDbImage;
    @ConfigProperty(name = "io.tackle.operator.ui.image")
    String uiImage;

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

        TackleStatus status = tackle.getStatus();
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
                Secret dockerhubSecret = kubernetesClient.secrets().load(getClass().getResourceAsStream("deployers/templates/docker-hub-image-puller.yaml")).get();
                dockerhubSecret.getMetadata().setNamespace(namespace);
                Map<String, String> data = Collections.singletonMap(".dockerconfigjson", dockerhubConfigJson);
                dockerhubSecret.setData(data);
                dockerhubSecret.getMetadata().getOwnerReferences().add(tackleOwnerReference);
                kubernetesClient.secrets().inNamespace(namespace).createOrReplace(dockerhubSecret);
            }
            else log.warn("No 'spec.dockerhubConfigJson' has been provided: anonymous image pulling from Dockerhub could suffer for rate limits.");
        }
        else log.info("Tackle resource without spec: default configuration will be applied");

        final Map<String, String> microservicesDeployed = new HashMap<>(4);
        // deploy Keycloak instance
        log.infof("Deploying Keycloak for '%s' in namespace '%s'", name, namespace);
        microservicesDeployed.put(KEYCLOAK, keycloakDeployer.createOrUpdateResource(tackle, keycloakImage, keycloakDbImage));
        final String tackleRealmUrl = String.format("http://%s:8080/auth/realms/tackle", microservicesDeployed.get(KEYCLOAK));
        final List<String> annotationConnectToKeycloak = List.of(microservicesDeployed.get(KEYCLOAK));

        // deploy microservices
        log.infof("Deploying Controls for '%s' in namespace '%s'", name, namespace);
        microservicesDeployed.put(CONTROLS, microserviceDeployer.createOrUpdateResource(tackle, CONTROLS, controlsImage, controlsDbImage,
                tackleRealmUrl, "controls_db", "controls", annotationConnectToKeycloak, Collections.unmodifiableMap(microservicesDeployed)));

        log.infof("Deploying Application Inventory for '%s' in namespace '%s'", name, namespace);
        microservicesDeployed.put(APPLICATION_INVENTORY, microserviceDeployer.createOrUpdateResource(tackle, APPLICATION_INVENTORY, applicationInventoryImage, applicationInventoryDbImage,
                tackleRealmUrl, "application_inventory_db", "application-inventory", annotationConnectToKeycloak, Collections.unmodifiableMap(microservicesDeployed)));

        log.infof("Deploying Pathfinder for '%s' in namespace '%s'", name, namespace);
        microservicesDeployed.put(PATHFINDER, microserviceDeployer.createOrUpdateResource(tackle, PATHFINDER, pathfinderImage, pathfinderDbImage,
                tackleRealmUrl, "pathfinder_db", "pathfinder", annotationConnectToKeycloak, Collections.unmodifiableMap(microservicesDeployed)));

        // deploy the UI instance
        log.infof("Deploying UI for '%s' in namespace '%s'", name, namespace);
        final String controlsRestName = String.format("%s-rest", microservicesDeployed.get(CONTROLS));
        final String pathfinderRestName = String.format("%s-rest", microservicesDeployed.get(PATHFINDER));
        final String applicationInventoryRestName = String.format("%s-rest", microservicesDeployed.get(APPLICATION_INVENTORY));
        uiDeployer.createOrUpdateResource(tackle, uiImage,
                String.format("http://%s:8080", controlsRestName),
                String.format("http://%s:8080", applicationInventoryRestName),
                String.format("http://%s:8080", pathfinderRestName),
                String.format("http://%s:8080", microservicesDeployed.get(KEYCLOAK)),
                Arrays.asList(controlsRestName, pathfinderRestName, applicationInventoryRestName, microservicesDeployed.get(KEYCLOAK)));

        if (status == null) {
            status = new TackleStatus();
            tackle.setStatus(status);
        }
        Condition condition = new ConditionBuilder()
                .withLastTransitionTime(ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT))
                .withType(CONDITION_TYPE_READY)
                .withStatus(CONDITION_STATUS_TRUE)
                .withReason("-")
                .withMessage("-")
                .build();
        status.getConditions().add(condition);
        return UpdateControl.updateStatusSubResource(tackle);
    }

}
