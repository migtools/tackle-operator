/*
 * Copyright © 2021 the Konveyor Contributors (https://konveyor.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tackle.operator.deployers;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.tackle.operator.Tackle;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.tackle.operator.Utils.metadataName;

@ApplicationScoped
public class MicroserviceDeployer {

    private final Logger log = Logger.getLogger(getClass());
    @Inject
    KubernetesClient kubernetesClient;
    @Inject
    PostgreSQLDeployer postgreSQLDeployer;
    @Inject
    RestDeployer restDeployer;

    public String createOrUpdateResource(Tackle tackle,
                                         String microserviceSuffix,
                                         String restImage,
                                         String postgreSQLImage,
                                         String oidcAuthServerUrl,
                                         String databaseSchema,
                                         String contextRoot,
                                         List<String> annotationConnectsTo,
                                         Map<String, String> microservicesDeployed) {
        final String namespace = tackle.getMetadata().getNamespace();
        final String name = metadataName(tackle, microserviceSuffix);

        log.infof("Execution createOrUpdateResource for '%s' in namespace '%s'", name, namespace);
        log.infof("Creating or updating PostgreSQL for Microservice '%s' in namespace '%s'", name, namespace);
        final String postgreSQLName = postgreSQLDeployer.createOrUpdateResource(kubernetesClient, tackle, name, postgreSQLImage, databaseSchema);

        log.infof("Creating or updating REST for Microservice '%s' in namespace '%s'", name, namespace);
        final List<String> connectsTo = new ArrayList<>(annotationConnectsTo);
        connectsTo.add(postgreSQLName);
        restDeployer.createOrUpdateResource(tackle, name,
                microserviceSuffix,
                restImage,
                oidcAuthServerUrl,
                contextRoot,
                postgreSQLName,
                databaseSchema,
                connectsTo,
                microservicesDeployed);

        return name;
    }
}
