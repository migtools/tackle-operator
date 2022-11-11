package io.tackle.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.mockwebserver.Context;
import io.quarkus.arc.profile.IfBuildProfile;
import okhttp3.mockwebserver.MockWebServer;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;

public class KubernetesTestClientProducer {
    @Produces
    @Singleton
    @IfBuildProfile("test")
    KubernetesClient makeDefaultClient(KubernetesMockServer server) {
        return server.createClient();
    }

    @Produces
    @Singleton
    @IfBuildProfile("test")
    KubernetesCrudRecorderDispatcher makeDispatcher() {
        return new KubernetesCrudRecorderDispatcher( Collections.emptyList());
    }

    @Produces
    @Singleton
    @IfBuildProfile("test")
    KubernetesMockServer makeKubernetesServer(KubernetesCrudRecorderDispatcher dispatcher) {
        MockWebServer webServer = new MockWebServer();
        KubernetesMockServer kubernetesServer = new KubernetesMockServer(new Context(), webServer, new HashMap<>(), dispatcher, false);
        return kubernetesServer;
    }

}
