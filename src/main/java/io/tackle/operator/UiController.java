package io.tackle.operator;

import io.fabric8.kubernetes.api.model.DeletionPropagation;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.jboss.logging.Logger;

import javax.inject.Inject;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class UiController extends AbstractController implements ResourceController<Ui> {

    private static final String RESOURCE_NAME_SUFFIX = "ui"; 
    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public UpdateControl<Ui> createOrUpdateResource(Ui ui, Context<Ui> context) {
        String namespace = ui.getMetadata().getNamespace();
        String name = metadataName(ui, RESOURCE_NAME_SUFFIX);
        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        Deployment deployment = kubernetesClient.apps().deployments().load(getClass().getResourceAsStream("templates/ui-deployment.yaml")).get();
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
                .getContainers()
                .get(0)
                .setImage(ui.getSpec().getImage());
        deployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setName(name);
        // all env must be set

        Service service = kubernetesClient.services().load(getClass().getResourceAsStream("templates/ui-service.yaml")).get();
        applyDefaultMetadata(service, name, namespace);
        
        Ingress ingress = loadYaml(Ingress.class, "templates/ui-ingress.yaml");
        applyDefaultMetadata(ingress, name, namespace);
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

        log.infof("Creating or updating Deployment '%s' in namespace '%s'", deployment.getMetadata().getName(), namespace);
        kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        log.infof("Creating or updating Service '%s' in namespace '%s'", service.getMetadata().getName(), namespace);
        kubernetesClient.services().inNamespace(namespace).createOrReplace(service);

        log.infof("Creating or updating Ingress '%s' in namespace '%s'", ingress.getMetadata().getName(), namespace);
        kubernetesClient.network().v1().ingresses().inNamespace(namespace).createOrReplace(ingress);

        BasicStatus status = new BasicStatus();
        ui.setStatus(status);
        return UpdateControl.updateCustomResource(ui);
    }

    @Override
    public DeleteControl deleteResource(Ui ui, Context<Ui> context) {
        String namespace = ui.getMetadata().getNamespace();
        String name = metadataName(ui, RESOURCE_NAME_SUFFIX);
        log.infof("Execution deleteResource for '%s' in namespace '%s'", name, namespace);

        log.infof("Deleting Ingress '%s' in namespace '%s'", name, namespace);
        Resource<Ingress> ingress =
                kubernetesClient
                        .network()
                        .v1()
                        .ingresses()
                        .inNamespace(namespace)
                        .withName(name);
        if (ingress.get() != null) {
            ingress.delete();
        }
        log.infof("Deleted Ingress '%s' in namespace '%s'", name, namespace);

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

        return DeleteControl.DEFAULT_DELETE;
    }

}
