# tackle-operator project

## Create namespace and CRDs
```shell
$ kubectl create namespace tackle-operator
$ kubectl apply -f src/main/resources/k8s/crds/crds.yaml
```

## Start in dev mode
```shell
$ ./mvnw clean quarkus:dev -Dquarkus.kubernetes-client.namespace=tackle-operator
```

## Start a local instance
To run locally for testing purposes, build it using
```shell
$ ./mvnw clean package
```
and then run executing
```shell
$ java -Dquarkus.kubernetes-client.namespace=tackle-operator -jar target/quarkus-app/quarkus-run.jar
```

## Create CR
```shell
$ kubectl apply -f src/main/resources/k8s/tackle/tackle.yaml -n tackle-operator
```

## Delete CR
```shell
$ kubectl delete -f src/main/resources/k8s/tackle/tackle.yaml -n tackle-operator
```

## Delete CRDs
```shell
$ kubectl delete -f src/main/resources/k8s/crds/crds.yaml
```

## Container image

### Build
```shell
$ ./mvnw clean package -Pnative -Dquarkus.container-image.build=true -Dquarkus.container-image.registry=quay.io -Dquarkus.container-image.tag=1.0.0-SNAPSHOT-native
$ podman push quay.io/$USERNAME/tackle-operator:1.0.0-SNAPSHOT-native
```
Alternatively, it can be also build in JVM mode using
```shell
$ ./mvnw clean package -Dquarkus.container-image.build=true -Dquarkus.container-image.registry=quay.io -Dquarkus.container-image.tag=1.0.0-SNAPSHOT-jvm
```

### Deploy
```shell
$ kubectl apply -f src/main/resources/k8s/operator.yaml -n tackle-operator
```
and then [Create CR](#create-cr).

### Undeploy
[Delete the Tackle CR](#delete-cr) and then:
```shell
$ kubectl delete -f src/main/resources/k8s/operator.yaml -n tackle-operator
```

## Operator Bundle

### Build
```shell
$ podman build --layers=false -f src/main/resources/releases/v1.0.0/Dockerfile -t quay.io/$USERNAME/tackle-operator-bundle:v1.0.0 src/main/resources/releases/v1.0.0/
$ podman push quay.io/$USERNAME/tackle-operator-bundle:v1.0.0
# install 'opm' CLI (ref. https://docs.openshift.com/container-platform/4.6/cli_reference/opm-cli.html) or clone 'operator-registry' repo (ref. https://github.com/operator-framework/operator-registry)
$ <path_to>/operator-registry/bin/opm index add --bundles quay.io/$USERNAME/tackle-operator-bundle:v1.0.0 --tag quay.io/$USERNAME/tackle-operator-test-catalog:v1.0.0 --container-tool podman --from-index quay.io/operatorhubio/catalog:latest
$ podman push quay.io/$USERNAME/tackle-operator-test-catalog:v1.0.0
```

### Install
```shell
$ minikube addons enable olm # only the first time
$ kubectl apply -f src/main/resources/releases/catalog-source.yaml
$ kubectl create -f src/main/resources/releases/tackle-operator.yaml -n tackle-operator
$ kubectl apply -f src/main/resources/k8s/tackle/tackle.yaml -n tackle-operator
```

### Uninstall
```shell
$ kubectl delete -f src/main/resources/k8s/tackle/tackle.yaml -n tackle-operator
$ kubectl delete -f src/main/resources/releases/tackle-operator.yaml -n tackle-operator
$ kubectl delete clusterserviceversions.operators.coreos.com tackle-operator.v1.0.0 -n tackle-operator
$ kubectl delete -f src/main/resources/releases/catalog-source.yaml
```
