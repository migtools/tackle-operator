package io.tackle.operator;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.jboss.logging.Logger;

import javax.inject.Inject;

import static io.tackle.operator.Utils.LABEL_NAME;
import static io.tackle.operator.Utils.applyDefaultMetadata;
import static io.tackle.operator.Utils.metadataName;

@Controller(namespaces = Controller.WATCH_CURRENT_NAMESPACE)
public class UiController implements ResourceController<Ui> {

    private static final String RESOURCE_NAME_SUFFIX = "ui"; 
    private final Logger log = Logger.getLogger(getClass());
    @Inject
    OpenShiftClient openShiftClient;

    @Override
    public UpdateControl<Ui> createOrUpdateResource(Ui ui, Context<Ui> context) {
        String namespace = ui.getMetadata().getNamespace();
        String name = metadataName(ui, RESOURCE_NAME_SUFFIX);

        MixedOperation<Ui, KubernetesResourceList<Ui>, Resource<Ui>> uiClient = openShiftClient.customResources(Ui.class);
        MixedOperation<Tackle, KubernetesResourceList<Tackle>, Resource<Tackle>> tackleClient = openShiftClient.customResources(Tackle.class);
        if (tackleClient.inNamespace(namespace).list().getItems().isEmpty()) {
            log.errorf("Standalone '%s' Ui CR isn't allowed: create a Tackle CR to instantiate Tackle application", name);
            uiClient.delete(ui);
            return UpdateControl.noUpdate();
        } else if (uiClient.inNamespace(namespace).list().getItems().size() > 1) {
            log.warnf("Only one Ui CR is allowed: '%s' is going to be deleted", name);
            uiClient.delete(ui);
            return UpdateControl.noUpdate();
        }

        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);

        Deployment deployment = openShiftClient.apps().deployments().load(getClass().getResourceAsStream("templates/ui-deployment.yaml")).get();
        applyDefaultMetadata(ui, deployment, RESOURCE_NAME_SUFFIX);
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

        Service service = openShiftClient.services().load(getClass().getResourceAsStream("templates/ui-service.yaml")).get();
        applyDefaultMetadata(ui, service, RESOURCE_NAME_SUFFIX);

        log.infof("Creating or updating Deployment '%s' in namespace '%s'", deployment.getMetadata().getName(), namespace);
        openShiftClient.apps().deployments().inNamespace(namespace).createOrReplace(deployment);

        log.infof("Creating or updating Service '%s' in namespace '%s'", service.getMetadata().getName(), namespace);
        openShiftClient.services().inNamespace(namespace).createOrReplace(service);

        if (!openShiftClient.isAdaptable(OpenShiftClient.class)) {
            Ingress ingress = openShiftClient.network().v1().ingresses().load(getClass().getResourceAsStream("templates/ui-ingress.yaml")).get();
            applyDefaultMetadata(ui, ingress, RESOURCE_NAME_SUFFIX);
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
            openShiftClient.network().v1().ingresses().inNamespace(namespace).createOrReplace(ingress);
        } else {
            final Route route = openShiftClient.routes().load(getClass().getResourceAsStream("templates/ui-route.yaml")).get();
            applyDefaultMetadata(ui, route, RESOURCE_NAME_SUFFIX);
            route
                    .getSpec()
                    .getTo()
                    .setName(name);
            log.infof("Creating or updating Route '%s' in namespace '%s'", route.getMetadata().getName(), namespace);
            openShiftClient.routes().inNamespace(namespace).createOrReplace(route);
        }

        BasicStatus status = new BasicStatus();
        ui.setStatus(status);
        return UpdateControl.updateCustomResource(ui);
    }

    @Override
    public DeleteControl deleteResource(Ui ui, Context<Ui> context) {
        String namespace = ui.getMetadata().getNamespace();
        String name = metadataName(ui, RESOURCE_NAME_SUFFIX);
        log.infof("Execution deleteResource for '%s' in namespace '%s'", name, namespace);
        return DeleteControl.DEFAULT_DELETE;
    }

}
