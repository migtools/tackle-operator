package io.tackle.operator;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSource;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
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

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class PostgreSQLController extends AbstractController implements ResourceController<PostgreSQL> {

    public static final String RESOURCE_NAME_SUFFIX = "postgresql"; 
    public static final String DATABASE_NAME = "database-name"; 
    public static final String DATABASE_PASSWORD = "database-password"; 
    public static final String DATABASE_USER = "database-user"; 
    private static final String USERNAME_FORMAT = "user-%s";
    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<PostgreSQL> createOrUpdateResource(PostgreSQL postgreSQL, Context<PostgreSQL> context) {
        String namespace = postgreSQL.getMetadata().getNamespace();
        String name = metadataName(postgreSQL, RESOURCE_NAME_SUFFIX);

        Secret secret = loadYaml(Secret.class, "templates/postgresql-secret.yaml");
        applyDefaultMetadata(secret, name, namespace);
        // worth letting the user setting them?
        String password = RandomStringUtils.randomAlphanumeric(16);
        secret
                .getData()
                .put(DATABASE_NAME, Base64.getEncoder().encodeToString(String.format("%s_db", metadataName(postgreSQL).replace("-", "_")).getBytes()));
        secret
                .getData()
                .put(DATABASE_PASSWORD, Base64.getEncoder().encodeToString(password.getBytes()));
        secret
                .getData()
                .put(DATABASE_USER, Base64.getEncoder().encodeToString(String.format(USERNAME_FORMAT, RandomStringUtils.randomAlphanumeric(4)).getBytes()));

        PersistentVolumeClaim pvc = loadYaml(PersistentVolumeClaim.class, "templates/postgresql-persistentvolumeclaim.yaml");
        applyDefaultMetadata(pvc, name, namespace);

        Deployment deployment = loadYaml(Deployment.class, "templates/postgresql-deployment.yaml");
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
                .setImage(postgreSQL.getSpec().getImage());
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setName(name);
        addDockerhubImagePullSecret(deployment, kubernetesClient.secrets().inNamespace(namespace));
        
        Service service = loadYaml(Service.class, "templates/postgresql-service.yaml");
        applyDefaultMetadata(service, name, namespace);
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

        BasicStatus status = new BasicStatus();
        postgreSQL.setStatus(status);
        return UpdateControl.updateCustomResource(postgreSQL);
    }

    @Override
    public DeleteControl deleteResource(PostgreSQL postgreSQL, Context<PostgreSQL> context) {
        String namespace = postgreSQL.getMetadata().getNamespace();
        String name = metadataName(postgreSQL, RESOURCE_NAME_SUFFIX);
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

        log.infof("Deleting PersistentVolumeClaim '%s' in namespace '%s'", name, namespace);
        Resource<PersistentVolumeClaim> pvc =
                kubernetesClient
                        .persistentVolumeClaims()
                        .inNamespace(namespace)
                        .withName(name);
        if (pvc.get() != null) {
            pvc.withPropagationPolicy(DeletionPropagation.FOREGROUND).delete();
        }
        log.infof("Deleted PersistentVolumeClaim '%s' in namespace '%s' with propagation", name, namespace);

        log.infof("Deleting Secret '%s' in namespace '%s'", name, namespace);
        Resource<Secret> secret =
                kubernetesClient
                        .secrets()
                        .inNamespace(namespace)
                        .withName(name);
        if (secret.get() != null) {
            secret.delete();
        }
        log.infof("Deleted Secret '%s' in namespace '%s'", name, namespace);

        return DeleteControl.DEFAULT_DELETE;
    }

}
