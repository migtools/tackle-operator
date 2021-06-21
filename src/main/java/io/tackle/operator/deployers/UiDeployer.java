package io.tackle.operator.deployers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.tackle.operator.Tackle;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;

import static io.tackle.operator.Utils.LABEL_NAME;
import static io.tackle.operator.Utils.addOpenshiftAnnotationConnectsTo;
import static io.tackle.operator.Utils.applyDefaultMetadata;
import static io.tackle.operator.Utils.metadataName;

@ApplicationScoped
public class UiDeployer {

    public static final String RESOURCE_NAME_SUFFIX = "ui";
    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    public void createOrUpdateResource(Tackle tackle,
                                       String image,
                                       String controlsApiUrl,
                                       String applicationInventoryApiUrl,
                                       String pathfinderApiUrl,
                                       String ssoApiUrl,
                                       List<String> annotationConnectsTo) {
        final String namespace = tackle.getMetadata().getNamespace();
        final String name = metadataName(tackle, RESOURCE_NAME_SUFFIX);

        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        Deployment deployment = kubernetesClient.apps().deployments().load(getClass().getResourceAsStream("templates/ui-deployment.yaml")).get();
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
                .getContainers()
                .get(0)
                .setImage(image);
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setName(name);
        List<EnvVar> envs =deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .getEnv();
        envs.get(0).setValue(controlsApiUrl);
        envs.get(1).setValue(applicationInventoryApiUrl);
        envs.get(2).setValue(pathfinderApiUrl);
        envs.get(5).setValue(ssoApiUrl);
        addOpenshiftAnnotationConnectsTo(deployment, annotationConnectsTo);

        Service service = kubernetesClient.services().load(getClass().getResourceAsStream("templates/ui-service.yaml")).get();
        applyDefaultMetadata(tackle, service, RESOURCE_NAME_SUFFIX);
        service
                .getSpec()
                .getSelector()
                .put(LABEL_NAME, name);

        log.infof("Creating or updating Deployment '%s' in namespace '%s'", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        log.infof("Creating or updating Service '%s' in namespace '%s'", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);

        if (!kubernetesClient.isAdaptable(OpenShiftClient.class)) {
            Ingress ingress = kubernetesClient.network().v1().ingresses().load(getClass().getResourceAsStream("templates/ui-ingress.yaml")).get();
            applyDefaultMetadata(tackle, ingress);
            ingress
                    .getSpec()
                    .getRules()
                    .get(0)
                    .getHttp()
                    .getPaths()
                    .get(0)
                    .getBackend()
                    .getService()
                    .setName(name);
            log.infof("Creating or updating Ingress '%s' in namespace '%s'", ingress.getMetadata().getName(), namespace);
            kubernetesClient.network().v1().ingresses().inNamespace(namespace).createOrReplace(ingress);
        } else {
            final OpenShiftClient openShiftClient = kubernetesClient.adapt(OpenShiftClient.class);
            final Route route = openShiftClient.routes().load(getClass().getResourceAsStream("templates/ui-route.yaml")).get();
            applyDefaultMetadata(tackle, route);
            route
                    .getSpec()
                    .getTo()
                    .setName(name);
            log.infof("Creating or updating Route '%s' in namespace '%s'", route.getMetadata().getName(), namespace);
            openShiftClient.routes().inNamespace(namespace).createOrReplace(route);
        }
    }

}
