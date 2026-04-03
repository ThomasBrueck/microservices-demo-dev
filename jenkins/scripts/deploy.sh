#!/bin/bash
set -e

VM_USER=$1
VM_HOST=$2
ACR_LOGIN_SERVER=$3
IMAGE_TAG=$4
ACR_NAME=$5

if [ -z "$VM_USER" ] || [ -z "$VM_HOST" ] || \
   [ -z "$ACR_LOGIN_SERVER" ] || [ -z "$IMAGE_TAG" ] || \
   [ -z "$ACR_NAME" ]; then
    echo "Uso: deploy.sh <user> <host> <acr-server> <tag> <acr-name>"
    exit 1
fi

echo ">>> Iniciando deploy en ${VM_HOST} con tag ${IMAGE_TAG}"

ssh -o StrictHostKeyChecking=no ${VM_USER}@${VM_HOST} \
    "bash /home/azureuser/deploy.sh ${IMAGE_TAG} ${ACR_LOGIN_SERVER} ${ACR_NAME}"

echo ">>> Deploy finalizado exitosamente"