package io.tackle.operator.deployers;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.tackle.operator.Tackle;
import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Base64;
import java.util.List;

import static io.tackle.operator.Utils.LABEL_NAME;
import static io.tackle.operator.Utils.addDockerhubImagePullSecret;
import static io.tackle.operator.Utils.addOpenshiftAnnotationConnectsTo;
import static io.tackle.operator.Utils.applyDefaultMetadata;
import static io.tackle.operator.Utils.metadataName;

@ApplicationScoped
public class KeycloakDeployer {

    public static final String ADMIN_USERNAME = "admin-username";
    public static final String ADMIN_PASSWORD = "admin-password";
    public static final String RESOURCE_NAME_SUFFIX = "keycloak";

    private final Logger log = Logger.getLogger(getClass());

    @Inject
    KubernetesClient kubernetesClient;
    @Inject
    PostgreSQLDeployer postgreSQLDeployer;

    public String createOrUpdateResource(Tackle tackle, String keycloakImage, String keycloakDbImage) {
        final String namespace = tackle.getMetadata().getNamespace();
        final String name = metadataName(tackle, RESOURCE_NAME_SUFFIX);

        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);
        // Deploy the PostgreSQL DB
        final String postgreSQLName = postgreSQLDeployer.createOrUpdateResource(kubernetesClient, tackle, name, keycloakDbImage, "keycloak_db");

        Secret secret = kubernetesClient.secrets().load(getClass().getResourceAsStream("templates/keycloak-secret.yaml")).get();
        applyDefaultMetadata(tackle, secret, RESOURCE_NAME_SUFFIX);
        String password = RandomStringUtils.randomAlphanumeric(16);
        secret
                .getData()
                .put(ADMIN_USERNAME, Base64.getEncoder().encodeToString("admin".getBytes()));
        secret
                .getData()
                .put(ADMIN_PASSWORD, Base64.getEncoder().encodeToString(password.getBytes()));

        ConfigMap configMap = kubernetesClient.configMaps().load(getClass().getResourceAsStream("templates/keycloak-configmap.yaml")).get();
        applyDefaultMetadata(tackle, configMap, RESOURCE_NAME_SUFFIX);

        Deployment deployment = kubernetesClient.apps().deployments().load(getClass().getResourceAsStream("templates/keycloak-deployment.yaml")).get();
        applyDefaultMetadata(tackle, deployment, RESOURCE_NAME_SUFFIX);
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
        envs.get(5).setValue(postgreSQLName);
        envs.get(6).getValueFrom().getSecretKeyRef().setName(postgreSQLName);
        envs.get(7).getValueFrom().getSecretKeyRef().setName(postgreSQLName);
        envs.get(8).getValueFrom().getSecretKeyRef().setName(postgreSQLName);

        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setImage(keycloakImage);
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setName(name);
        addDockerhubImagePullSecret(deployment, kubernetesClient.secrets().inNamespace(namespace));
        addOpenshiftAnnotationConnectsTo(deployment, postgreSQLName);

        Service service = kubernetesClient.services().load(getClass().getResourceAsStream("templates/keycloak-service.yaml")).get();
        applyDefaultMetadata(tackle, service, RESOURCE_NAME_SUFFIX);
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
        return name;
    }

}
