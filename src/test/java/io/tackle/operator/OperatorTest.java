package io.tackle.operator;

import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.Operator;
import io.quarkus.test.junit.QuarkusTest;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class OperatorTest {
    private static final String testCRDeployment = "/k8s/tackle/tackle.yaml";

    private static final String testApp = "tackle-sample";

    private static final String testNamespace = "test"; // hardcoded in the KubernetesMockServer.createClient

    @Inject
    KubernetesCrudRecorderDispatcher dispatcher;

    @Inject
    Operator operator;

    @Inject
    KubernetesClient client;

    private Tackle tackleResource;

    private void loadWindupResource() {
        InputStream fileStream = this.getClass().getResourceAsStream(testCRDeployment);
        tackleResource = Serialization.unmarshal(fileStream, Tackle.class);
        tackleResource.getMetadata().setNamespace(testNamespace);
        tackleResource.getMetadata().setUid("uid: 4e4d714c-6d27-41e1-86df-4a58900ca5d0");
    }

    @Test
    public void onAddCR_shouldServerReceiveExactCalls() {
        operator.start();

        NonNamespaceOperation<Tackle, KubernetesResourceList<Tackle>, Resource<Tackle>> resource = client.customResources(Tackle.class).inNamespace(testNamespace);
        if (resource.withName(testApp).get() != null) {
            resource.withName(testApp).delete();
            Awaitility
                .await()
                .atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(resource.withName(testApp).get()));
        }
        dispatcher.setRequests(new ArrayList<Request>());
        if (this.tackleResource == null) {
            loadWindupResource();
        }
        resource.create(this.tackleResource);

        Awaitility
            .await()
            .atMost(20, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertEquals(1, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.method) && e.path.contains("ingress")).count());
                assertEquals(4, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.method) && e.path.contains("persistentvolumeclaim")).count());
                assertEquals(9, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.method) && e.path.contains("deployments") ).count());
                assertEquals(9, dispatcher.getRequests().stream().filter(e-> "POST".equalsIgnoreCase(e.method) && e.path.contains("service")).count());
            });
    }

}
