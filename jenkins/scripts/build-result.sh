#!/bin/bash
set -e

echo ">>> Entrando al directorio result"
cd result

echo ">>> Instalando dependencias de Node.js"
npm ci

echo ">>> Ejecutando tests"
npm test

echo ">>> Build de result completado exitosamente"
echo "Dependencias instaladas: $(npm list --depth=0 2>/dev/null | wc -l) paquetes"