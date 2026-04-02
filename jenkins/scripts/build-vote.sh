#!/bin/bash
set -e

echo ">>> Entrando al directorio vote"
cd vote

echo ">>> Ejecutando tests con Maven"
mvn clean test \
    --batch-mode \
    --no-transfer-progress

echo ">>> Compilando JAR"
mvn package \
    --batch-mode \
    --no-transfer-progress \
    -DskipTests

echo ">>> Build de vote completado exitosamente"
ls -lh target/*.jar