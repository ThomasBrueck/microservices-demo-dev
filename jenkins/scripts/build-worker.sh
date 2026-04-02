#!/bin/bash
set -e

echo ">>> Entrando al directorio worker"
cd worker

echo ">>> Descargando dependencias de Go"
go mod download

echo ">>> Ejecutando tests"
go test ./... -v -cover

echo ">>> Compilando binario"
go build -o worker .

echo ">>> Build de worker completado exitosamente"
ls -lh worker