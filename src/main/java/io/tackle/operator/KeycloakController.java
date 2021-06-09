package io.tackle.operator;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.util.Base64;
import java.util.List;

import static io.tackle.operator.Utils.LABEL_NAME;
import static io.tackle.operator.Utils.addDockerhubImagePullSecret;
import static io.tackle.operator.Utils.applyDefaultMetadata;
import static io.tackle.operator.Utils.metadataName;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class KeycloakController implements ResourceController<Keycloak> {

    public static final String ADMIN_USERNAME = "admin-username";
    public static final String ADMIN_PASSWORD = "admin-password";

    private final Logger log = Logger.getLogger(getClass());

    @Inject
    KubernetesClient kubernetesClient;
    @Inject
    PostgreSQLDeployer postgreSQLDeployer;

    @Override
    public UpdateControl<Keycloak> createOrUpdateResource(Keycloak keycloak, Context<Keycloak> context) {
        String namespace = keycloak.getMetadata().getNamespace();
        // Keycloak is unique, no need for suffixes in the name
        String name = metadataName(keycloak);

        MixedOperation<Keycloak, KubernetesResourceList<Keycloak>, Resource<Keycloak>> keycloakClient = kubernetesClient.customResources(Keycloak.class);
        MixedOperation<Tackle, KubernetesResourceList<Tackle>, Resource<Tackle>> tackleClient = kubernetesClient.customResources(Tackle.class);
        if (tackleClient.inNamespace(namespace).list().getItems().isEmpty()) {
            log.errorf("Standalone '%s' Keycloak CR isn't allowed: create a Tackle CR to instantiate Tackle application", name);
            keycloakClient.delete(keycloak);
            return UpdateControl.noUpdate();
        } else if (keycloakClient.inNamespace(namespace).list().getItems().size() > 1) {
            log.warnf("Only one Keycloak CR is allowed: '%s' is going to be deleted", name);
            keycloakClient.delete(keycloak);
            return UpdateControl.noUpdate();
        }

        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);
        // Deploy the PostgreSQL DB
        postgreSQLDeployer.createOrUpdateResource(kubernetesClient, keycloak);

        Secret secret = kubernetesClient.secrets().load(getClass().getResourceAsStream("templates/keycloak-secret.yaml")).get();
        applyDefaultMetadata(keycloak, secret);
        String password = RandomStringUtils.randomAlphanumeric(16);
        secret
                .getData()
                .put(ADMIN_USERNAME, Base64.getEncoder().encodeToString("admin".getBytes()));
        secret
                .getData()
                .put(ADMIN_PASSWORD, Base64.getEncoder().encodeToString(password.getBytes()));

        ConfigMap configMap = kubernetesClient.configMaps().load(getClass().getResourceAsStream("templates/keycloak-configmap.yaml")).get();
        applyDefaultMetadata(keycloak, configMap);

        Deployment deployment = kubernetesClient.apps().deployments().load(getClass().getResourceAsStream("templates/keycloak-deployment.yaml")).get();
        applyDefaultMetadata(keycloak, deployment);
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
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getVolumes()
                .get(0)
                .getConfigMap()
                .setName(configMap.getMetadata().getName());
        List<EnvVar> envs =deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv();
        // env are positional in the provided yaml deployment
        // Keycloak Admin credential
        envs.get(0).getValueFrom().getSecretKeyRef().setName(name);
        envs.get(1).getValueFrom().getSecretKeyRef().setName(name);
        // DB credentials from secret
        envs.get(7).getValueFrom().getSecretKeyRef().setName(metadataName(keycloak, PostgreSQLDeployer.RESOURCE_NAME_SUFFIX));
        envs.get(8).getValueFrom().getSecretKeyRef().setName(metadataName(keycloak, PostgreSQLDeployer.RESOURCE_NAME_SUFFIX));

        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setImage(keycloak.getSpec().getRestImage());
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setName(name);
        addDockerhubImagePullSecret(deployment, kubernetesClient.secrets().inNamespace(namespace));

        Service service = kubernetesClient.services().load(getClass().getResourceAsStream("templates/keycloak-service.yaml")).get();
        applyDefaultMetadata(keycloak, service);
        service
                .getSpec()
                .getSelector()
                .put(LABEL_NAME, name);

        log.infof("Creating or updating Secret '%s' in namespace '%s'", secret.getMetadata().getName(), namespace);
        kubernetesClient.secrets().inNamespace(namespace).createOrReplace(secret);

        log.infof("Creating or updating ConfigMap '%s' in namespace '%s'", configMap.getMetadata().getName(), namespace);
        kubernetesClient.configMaps().inNamespace(namespace).createOrReplace(configMap);

        log.infof("Creating or updating Deployment '%s' in namespace '%s'", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        log.infof("Creating or updating Service '%s' in namespace '%s'", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);

        BasicStatus status = new BasicStatus();
        keycloak.setStatus(status);
        return UpdateControl.updateCustomResource(keycloak);
    }

    @Override
    public DeleteControl deleteResource(Keycloak keycloak, Context<Keycloak> context) {
        String namespace = keycloak.getMetadata().getNamespace();
        String name = metadataName(keycloak);
        log.infof("Execution deleteResource for '%s' in namespace '%s'", name, namespace);
        return DeleteControl.DEFAULT_DELETE;
    }

}
