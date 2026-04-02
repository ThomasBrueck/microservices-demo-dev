#!/bin/bash
set -e

VM_USER=$1
VM_HOST=$2
ACR_LOGIN_SERVER=$3
IMAGE_TAG=$4

if [ -z "$VM_USER" ] || [ -z "$VM_HOST" ] || \
   [ -z "$ACR_LOGIN_SERVER" ] || [ -z "$IMAGE_TAG" ]; then
    echo "Uso: deploy.sh <user> <host> <acr-server> <tag>"
    exit 1
fi

echo ">>> Iniciando deploy en ${VM_HOST} con tag ${IMAGE_TAG}"

ssh -o StrictHostKeyChecking=no \
    ${VM_USER}@${VM_HOST} \
    ACR_LOGIN_SERVER=${ACR_LOGIN_SERVER} \
    IMAGE_TAG=${IMAGE_TAG} \
    'bash -s' << 'REMOTE_SCRIPT'

    set -e
    echo "=== Conectado a la VM App ==="

    # Va al directorio del proyecto
    cd /home/azureuser/microservices-demo

    # Trae los ultimos cambios del docker-compose.yml
    git pull origin main

    # Exporta las variables para docker-compose
    export IMAGE_TAG=${IMAGE_TAG}
    export ACR_LOGIN_SERVER=${ACR_LOGIN_SERVER}

    # Descarga las nuevas imagenes del ACR
    echo "=== Descargando nuevas imagenes del ACR ==="
    docker compose pull vote worker result

    # Levanta los contenedores actualizados sin tiempo de caida
    echo "=== Actualizando contenedores ==="
    docker compose up -d \
        --no-deps \
        --force-recreate \
        vote worker result

    # Muestra el estado de todos los contenedores
    echo "=== Estado actual de los contenedores ==="
    docker compose ps

    echo "=== Deploy completado en la VM App ==="

REMOTE_SCRIPT

echo ">>> Deploy finalizado exitosamente"