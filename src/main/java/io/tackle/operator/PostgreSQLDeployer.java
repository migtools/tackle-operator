package io.tackle.operator;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import java.util.Base64;
import java.util.List;

import static io.tackle.operator.Utils.LABEL_NAME;
import static io.tackle.operator.Utils.addDockerhubImagePullSecret;
import static io.tackle.operator.Utils.applyDefaultMetadata;
import static io.tackle.operator.Utils.metadataName;

@ApplicationScoped
public class PostgreSQLDeployer {

    public static final String RESOURCE_NAME_SUFFIX = "postgresql"; 
    public static final String DATABASE_NAME = "database-name"; 
    public static final String DATABASE_PASSWORD = "database-password"; 
    public static final String DATABASE_USER = "database-user"; 
    private static final String USERNAME_FORMAT = "user-%s";
    private final Logger log = Logger.getLogger(getClass());

    public <T> void createOrUpdateResource(KubernetesClient kubernetesClient, CustomResource<MicroserviceSpec, T> parentCustomResource) {
        final String namespace = parentCustomResource.getMetadata().getNamespace();
        final String parentName = parentCustomResource.getMetadata().getName();
        final String name = metadataName(parentCustomResource, RESOURCE_NAME_SUFFIX);
        Secret secret = kubernetesClient.secrets().load(getClass().getResourceAsStream("templates/postgresql-secret.yaml")).get();
        applyDefaultMetadata(parentCustomResource, secret, RESOURCE_NAME_SUFFIX);
        // worth letting the user setting them?
        String password = RandomStringUtils.randomAlphanumeric(16);
        secret
                .getData()
                .put(DATABASE_NAME, Base64.getEncoder().encodeToString(String.format("%s_db", parentName.replace("-", "_")).getBytes()));
        secret
                .getData()
                .put(DATABASE_PASSWORD, Base64.getEncoder().encodeToString(password.getBytes()));
        secret
                .getData()
                .put(DATABASE_USER, Base64.getEncoder().encodeToString(String.format(USERNAME_FORMAT, RandomStringUtils.randomAlphanumeric(4)).getBytes()));

        PersistentVolumeClaim pvc = kubernetesClient.persistentVolumeClaims().load(getClass().getResourceAsStream("templates/postgresql-persistentvolumeclaim.yaml")).get();
        applyDefaultMetadata(parentCustomResource, pvc, RESOURCE_NAME_SUFFIX);

        Deployment deployment = kubernetesClient.apps().deployments().load(getClass().getResourceAsStream("templates/postgresql-deployment.yaml")).get();
        applyDefaultMetadata(parentCustomResource, deployment, RESOURCE_NAME_SUFFIX);
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
                .setPersistentVolumeClaim(new PersistentVolumeClaimVolumeSource(pvc.getMetadata().getName(), false));
        List<EnvVar> envs =deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv();
        // env are positional in the provided yaml deployment
        envs.get(0).getValueFrom().getSecretKeyRef().setName(name);
        envs.get(1).getValueFrom().getSecretKeyRef().setName(name);
        envs.get(2).getValueFrom().getSecretKeyRef().setName(name);
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setImage(parentCustomResource.getSpec().getPostgreSQLImage());
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setName(name);
        addDockerhubImagePullSecret(deployment, kubernetesClient.secrets().inNamespace(namespace));
        
        Service service = kubernetesClient.services().load(getClass().getResourceAsStream("templates/postgresql-service.yaml")).get();
        applyDefaultMetadata(parentCustomResource, service, RESOURCE_NAME_SUFFIX);
        service
                .getSpec()
                .getSelector()
                .put(LABEL_NAME, name);

        // if the secret is already there, maybe changing the user and pwd is not a good idea?
        if (kubernetesClient.secrets().inNamespace(namespace).withName(name).get() == null) {
            log.infof("Creating or updating Secret '%s' in namespace '%s'", secret.getMetadata().getName(), namespace);
            kubernetesClient.secrets().inNamespace(namespace).createOrReplace(secret);
        } else {
            log.infof("No changes done to Secret '%s' in namespace '%s'", pvc.getMetadata().getName(), namespace);
        }

        if (kubernetesClient.persistentVolumeClaims().inNamespace(namespace).withName(name).get() == null) {
            log.infof("Creating or updating PersistentVolumeClaim '%s' in namespace '%s'", pvc.getMetadata().getName(), namespace);
            kubernetesClient.persistentVolumeClaims().inNamespace(namespace).createOrReplace(pvc);
        } else {
            log.infof("No changes done to PersistentVolumeClaim '%s' in namespace '%s'", pvc.getMetadata().getName(), namespace);
        }

        log.infof("Creating or updating Deployment '%s' in namespace '%s'", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        log.infof("Creating or updating Service '%s' in namespace '%s'", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);
    }

}
