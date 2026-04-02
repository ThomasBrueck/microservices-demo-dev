#!/bin/bash
set -e

ACTION=$1          # "build" o "push"
IMAGE_TAG=$2       # numero de build de Jenkins
ACR_LOGIN_SERVER=$3 # turegistro.azurecr.io

if [ -z "$ACTION" ] || [ -z "$IMAGE_TAG" ] || [ -z "$ACR_LOGIN_SERVER" ]; then
    echo "Uso: docker-build-push.sh <build|push> <tag> <acr-server>"
    exit 1
fi

SERVICES=("vote" "worker" "result")

if [ "$ACTION" == "build" ]; then
    echo ">>> Construyendo imagenes Docker con tag: ${IMAGE_TAG}"

    for SERVICE in "${SERVICES[@]}"; do
        echo "--- Construyendo imagen: ${SERVICE}"
        docker build \
            --tag "${ACR_LOGIN_SERVER}/${SERVICE}:${IMAGE_TAG}" \
            --tag "${ACR_LOGIN_SERVER}/${SERVICE}:latest" \
            --file "./${SERVICE}/Dockerfile" \
            "./${SERVICE}"
        echo "--- Imagen ${SERVICE} construida correctamente"
    done

elif [ "$ACTION" == "push" ]; then
    echo ">>> Subiendo imagenes al ACR con tag: ${IMAGE_TAG}"

    for SERVICE in "${SERVICES[@]}"; do
        echo "--- Subiendo imagen: ${SERVICE}:${IMAGE_TAG}"
        docker push "${ACR_LOGIN_SERVER}/${SERVICE}:${IMAGE_TAG}"

        echo "--- Subiendo imagen: ${SERVICE}:latest"
        docker push "${ACR_LOGIN_SERVER}/${SERVICE}:latest"

        echo "--- Imagen ${SERVICE} subida correctamente"
    done

else
    echo "Accion desconocida: ${ACTION}. Usa 'build' o 'push'"
    exit 1
fi

echo ">>> Accion '${ACTION}' completada para todos los servicios"