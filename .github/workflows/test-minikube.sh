#!/bin/sh
set -x # print commands executed
set -e # exit immediatly if any command fails

# Build the operator image locally
tackle_ns=tackle2
cd ../..
./mvnw clean package -Pnative -DskipTests \
            -Dquarkus.native.container-build=true \
            -Dquarkus.native.container-runtime=docker \
            -Dquarkus.container-image.build=true  \
            -Dquarkus.container-image.tag=test \
            -Dquarkus.container-image.push=false

# start Minikube and deploy the image
minikube start --addons ingress
kubectl create namespace $tackle_ns
minikube image load quay.io/konveyor/tackle-operator:test

# deploy the operator
#kubectl create namespace tackle
cd src/main/resources/k8s
kubectl apply -f crds/crds.yaml -n $tackle_ns
sed "s/{user}/konveyor/g" operator.yaml | kubectl apply -n $tackle_ns -f -
sleep 10

# create a Tackle instance
kubectl apply -f tackle/tackle.yaml -n $tackle_ns
sleep 60

# check the number of objects (operands) created by the operator is the expected
test "4" = "$(kubectl get pvc -n $tackle_ns -o name | wc -l)"
test "10" = "$(kubectl get pods -n $tackle_ns -o name | wc -l)"
test "9" = "$(kubectl get service -n $tackle_ns -o name | wc -l)"
test "10" = "$(kubectl get deployments -n $tackle_ns -o name | wc -l)"

# checking all pods are ready
test "10" = "$(kubectl get pods -n $tackle_ns -o json  | jq -r '.items[] | select(.status.phase == "Running" or ([ .status.conditions[] | select(.type == "Ready" and .status == true) ] | length ) == 1 ) | .metadata.namespace + "/" + .metadata.name' | wc -l)"

# doing API tests
minikube_ip=$(minikube ip)
access_token=$(\
    curl -X POST http://$minikube_ip/auth/realms/tackle/protocol/openid-connect/token \
    --user backend-service:secret \
    -H 'content-type: application/x-www-form-urlencoded' \
    -d 'username=tackle&password=password&grant_type=password' | jq --raw-output '.access_token' \
 )
test "200" = "$(curl --write-out "%{http_code}\n" --silent --output /dev/null -X GET "http://$minikube_ip:/pathfinder/assessments/risk" \
  -d "[{'applicationId':1}]" \
  -H "Content-Type: application/json" -H "Accept: application/json" -H "Authorization: Bearer $access_token")"

test "200" = "$(curl --write-out "%{http_code}\n" --silent --output /dev/null -X GET "http://$minikube_ip/application_inventory/adoptionplan" \
  -d "[{'applicationId':1}]" \
  -H "Content-Type: application/json" -H "Accept: application/json" -H "Authorization: Bearer $access_token")"

test "200" = "$(curl --write-out "%{http_code}\n" --silent --output /dev/null -X GET "http://$minikube_ip/controls/business-service" \
  -H "Content-Type: application/json" -H "Accept: application/json" -H "Authorization: Bearer $access_token")"

echo "Test Successfully passed :)"