# Estrategia de Branching para Desarrolladores — GitHub Flow

## Tabla de contenido

1. [Introducción y contexto](#1-introducción-y-contexto)
2. [¿Por qué GitHub Flow?](#2-por-qué-github-flow)
3. [Estructura de ramas](#3-estructura-de-ramas)
4. [Convención de nombres de ramas](#4-convención-de-nombres-de-ramas)
5. [Flujo de trabajo completo paso a paso](#5-flujo-de-trabajo-completo-paso-a-paso)
6. [Convención de commits](#6-convención-de-commits)
7. [Pull Requests](#7-pull-requests)
8. [Reglas de protección de la rama main](#8-reglas-de-protección-de-la-rama-main)
9. [Manejo de conflictos](#9-manejo-de-conflictos)
10. [Manejo de bugs urgentes en producción](#10-manejo-de-bugs-urgentes-en-producción)
11. [Qué NO hacer](#11-qué-no-hacer)
12. [Referencia rápida de comandos](#12-referencia-rápida-de-comandos)

---

## 1. Introducción y contexto

Este documento define la estrategia oficial de branching para el equipo de
desarrollo del proyecto **Microservices Demo**. Su propósito es establecer
reglas claras y consistentes que todos los miembros del equipo deben seguir
al trabajar con Git y GitHub.

El proyecto está compuesto por tres microservicios desarrollados en lenguajes
distintos:

| Microservicio | Lenguaje | Responsabilidad |
|---------------|----------|-----------------|
| `vote` | Java | Frontend de votación |
| `worker` | Go | Procesa votos desde Kafka y escribe en PostgreSQL |
| `result` | Node.js | Muestra resultados en tiempo real |

Dado que múltiples desarrolladores pueden trabajar simultáneamente en distintos
microservicios, es indispensable tener una estrategia de branching que evite
conflictos, garantice la calidad del código y mantenga la rama principal siempre
en un estado desplegable.

---

## 2. ¿Por qué GitHub Flow?

Se adoptó **GitHub Flow** como estrategia de branching por las siguientes razones:

### Simplicidad
GitHub Flow tiene una sola rama de larga duración (`main`) y ramas de trabajo
temporales. No existen ramas como `develop`, `release` o `hotfix` que añaden
complejidad innecesaria para un equipo ágil pequeño.

### Compatibilidad con entrega continua
En GitHub Flow, `main` siempre está en un estado desplegable. Esto significa
que en cualquier momento se puede hacer un despliegue desde `main` sin
preparación adicional. Este principio es fundamental para la metodología ágil
que sigue el equipo.

### Integración natural con GitHub Actions
Los pipelines de CI/CD del proyecto están configurados para ejecutarse
automáticamente cuando hay cambios en `main`. GitHub Flow se integra
directamente con este flujo sin configuración adicional.

### Comparación con otras estrategias

| Característica | GitHub Flow | Gitflow | Trunk-Based |
|----------------|-------------|---------|-------------|
| Complejidad | Baja | Alta | Muy baja |
| Ramas de larga duración | Solo `main` | `main` + `develop` | Solo `main` |
| Adecuado para CD | Sí | No idealmente | Sí |
| Ideal para equipos pequeños | Sí | No | Sí |
| Requiere releases formales | No | Sí | No |

---

## 3. Estructura de ramas

### Rama `main` (única rama permanente)

`main` es la única rama que existe de forma permanente en el repositorio.
Representa el estado actual del proyecto en producción.

**Reglas estrictas de `main`:**
- Siempre está en un estado funcional y desplegable.
- Nadie puede hacer `git push` directo a `main` bajo ninguna circunstancia.
- Todo cambio entra únicamente a través de un Pull Request aprobado.
- El pipeline de CI debe estar verde antes de permitir cualquier merge.
- Requiere mínimo 1 aprobación de otro miembro del equipo.

### Ramas temporales de trabajo

Todos los demás tipos de ramas son **temporales**. Se crean para un propósito
específico y se eliminan inmediatamente después de hacer merge a `main`.

No deben existir ramas de trabajo abandonadas por más de una semana sin
actividad. Si una rama lleva mucho tiempo sin merge, el equipo debe discutir
si el trabajo sigue siendo relevante.

---

## 4. Convención de nombres de ramas

El nombre de una rama debe comunicar inmediatamente **qué tipo de cambio
contiene** y **qué hace específicamente**. Se compone de dos partes:
```
<prefijo>/<descripcion-en-kebab-case>
```

### Prefijos disponibles

| Prefijo | Cuándo usarlo | Ejemplo |
|---------|--------------|---------|
| `feature/` | Desarrollo de una nueva funcionalidad | `feature/kafka-dead-letter-queue` |
| `fix/` | Corrección de un bug | `fix/worker-postgres-null-pointer` |
| `chore/` | Mantenimiento, actualizaciones de dependencias, refactoring | `chore/upgrade-go-1.22` |
| `docs/` | Cambios exclusivamente de documentación | `docs/add-api-endpoints-readme` |
| `test/` | Agregar o corregir tests sin cambiar funcionalidad | `test/add-vote-service-unit-tests` |

### Reglas de nomenclatura

- Usar exclusivamente **minúsculas**.
- Separar palabras con **guiones** (kebab-case), nunca con espacios ni guiones bajos.
- La descripción debe ser **específica y concisa**: debe explicar qué hace el
  cambio, no solo en qué área trabaja.
- Máximo **50 caracteres** en la descripción después del prefijo.
- Usar **inglés** para mantener consistencia con el código fuente.

### Ejemplos válidos
```
feature/add-retry-logic-to-kafka-consumer
feature/vote-result-pagination
fix/result-websocket-connection-drop
fix/worker-duplicate-vote-processing
chore/update-bitnami-kafka-helm-chart
chore/remove-unused-postgres-indexes
docs/add-circuit-breaker-documentation
test/add-worker-integration-tests
```

### Ejemplos inválidos
```
mi-rama              ← sin prefijo
feature/arreglo      ← demasiado vago, no describe qué arregla
feature/KAFKA_RETRY  ← mayúsculas y guiones bajos
fix                  ← sin descripción
Feature/AddLogin     ← mayúsculas y PascalCase
```

---

## 5. Flujo de trabajo completo paso a paso

Este es el flujo que todo desarrollador debe seguir para cada tarea o cambio,
sin excepción.

### Paso 1 — Sincronizar `main` antes de empezar

Antes de crear cualquier rama nueva, siempre debes asegurarte de tener la
versión más reciente de `main`. Nunca crees una rama desde un `main` desactualizado.
```bash
git checkout main
git pull origin main
```

### Paso 2 — Crear la rama de trabajo

Con `main` actualizado, crea tu rama con el prefijo y nombre correspondiente:
```bash
git checkout -b feature/nombre-descriptivo
```

Este comando hace dos cosas a la vez: crea la rama y te mueve a ella.
Ahora estás trabajando en tu propia copia aislada del proyecto.

### Paso 3 — Desarrollar y hacer commits frecuentes

Trabaja en tu tarea haciendo commits pequeños y frecuentes. Cada commit debe
representar un avance lógico e independiente, no acumules todos los cambios
en un solo commit al final.
```bash
# Ver qué archivos modificaste
git status

# Agregar archivos específicos (preferido sobre git add .)
git add worker/main.go
git add worker/circuit_breaker.go

# O agregar todos los cambios si estás seguro
git add .

# Hacer el commit con mensaje descriptivo
git commit -m "feat(worker): add circuit breaker for postgres connection"
```

**¿Con qué frecuencia hacer commit?**
- Cada vez que completas una parte lógica del trabajo.
- Cada vez que el código compila y los tests pasan.
- Antes de salir de trabajar, aunque el trabajo no esté terminado.
- Nunca al final del día con todo acumulado en un solo commit.

### Paso 4 — Subir la rama a GitHub frecuentemente

No trabajes en local durante días sin subir tu rama. Sube los cambios
regularmente para que el equipo sepa en qué estás trabajando y como
respaldo de tu trabajo.
```bash
# Primera vez que subes la rama
git push -u origin feature/nombre-descriptivo

# Las siguientes veces
git push
```

### Paso 5 — Mantener la rama actualizada con `main`

Si tu rama tarda varios días, es probable que `main` haya recibido nuevos
cambios de otros compañeros. Debes incorporar esos cambios a tu rama para
evitar conflictos grandes al momento del merge.
```bash
# Mientras estás en tu rama
git fetch origin
git rebase origin/main
```

Se prefiere `rebase` sobre `merge` para mantener un historial lineal
y limpio en tu rama de trabajo.

### Paso 6 — Abrir el Pull Request

Cuando tu trabajo está completo y los tests pasan, abre un Pull Request en
GitHub hacia `main`.

Para abrir el PR:
1. Ve al repositorio en GitHub.
2. GitHub mostrará automáticamente un botón **"Compare & pull request"**
   si subiste una rama recientemente.
3. Completa el título y la descripción del PR siguiendo la plantilla
   definida en la sección de Pull Requests.

### Paso 7 — Revisión y aprobación

Un compañero del equipo revisa el código, deja comentarios si hay algo
que mejorar, y aprueba el PR cuando está conforme. El autor del PR
es responsable de responder los comentarios y hacer los ajustes necesarios.

### Paso 8 — Merge a `main`

Una vez aprobado el PR y con el CI en verde, se hace merge usando
**Squash and Merge** para mantener el historial de `main` limpio.

En el mensaje del squash commit se escribe:
```
tipo(scope): descripción corta del cambio
```

Por ejemplo:
```
feat(worker): add circuit breaker for postgres connection
```

### Paso 9 — Eliminar la rama

Después del merge, la rama de trabajo ya no tiene razón de existir.
GitHub ofrece un botón **"Delete branch"** automáticamente al hacer merge.
Úsalo siempre.

También puedes eliminarla desde tu máquina local:
```bash
git checkout main
git pull origin main
git branch -d feature/nombre-descriptivo
```

---

## 6. Convención de commits

Se usa el estándar **Conventional Commits** para todos los mensajes de commit
del proyecto. Este estándar hace el historial legible y permite generar
changelogs automáticamente.

### Formato
```
<tipo>(<scope>): <descripción corta>

[cuerpo opcional]

[footer opcional]
```

### Tipos de commit

| Tipo | Cuándo usarlo |
|------|--------------|
| `feat` | Se agrega una nueva funcionalidad |
| `fix` | Se corrige un bug |
| `test` | Se agregan o corrigen tests |
| `chore` | Mantenimiento que no afecta funcionalidad |
| `docs` | Solo cambios en documentación |
| `refactor` | Refactoring sin cambio de comportamiento externo |
| `perf` | Mejora de rendimiento |
| `ci` | Cambios en pipelines o configuración de CI/CD |

### Scope (alcance)

El scope indica qué microservicio o módulo afecta el commit:

| Scope | Cuándo usarlo |
|-------|--------------|
| `vote` | Cambios en el servicio de votación (Java) |
| `worker` | Cambios en el worker (Go) |
| `result` | Cambios en el servicio de resultados (Node.js) |
| `infra` | Cambios en archivos de infraestructura |
| `deps` | Actualización de dependencias |

### Reglas del mensaje

- La descripción corta va en **infinitivo** y en **inglés**.
- Máximo **72 caracteres** en la primera línea.
- No terminar con punto.
- El cuerpo (opcional) explica el **por qué** del cambio, no el qué
  (el qué ya lo dice el código).

### Ejemplos válidos
```bash
feat(worker): add circuit breaker for postgres connection

fix(result): resolve websocket disconnection on page reload

test(vote): add unit tests for duplicate vote validation

chore(deps): upgrade kafka client library to 3.6.0

docs(worker): add circuit breaker usage documentation

refactor(worker): extract database connection to separate package

ci: add docker build step to vote service pipeline
```

### Ejemplos inválidos
```bash
fix: arregle el bug                     ← demasiado vago, en español
WIP                                     ← no describe nada
feat(worker): Added the circuit breaker ← pasado, no infinitivo, con punto
update files                            ← no describe nada útil
asdfgh                                  ← inaceptable
```

### Commits de trabajo en progreso (WIP)

Si necesitas subir trabajo incompleto (por ejemplo, al final del día),
usa el prefijo `wip:` que será aplastado en el squash merge y no
quedará en el historial de `main`:
```bash
git commit -m "wip(worker): circuit breaker implementation in progress"
```

---

## 7. Pull Requests

### Cuándo abrir un PR

Abre el PR cuando:
- El trabajo está completo y los tests pasan localmente.
- El código compila sin errores.
- Hiciste al menos una revisión propia del código (code self-review).

### Título del PR

El título debe seguir el mismo formato que Conventional Commits:
```
feat(worker): add circuit breaker for postgres connection
fix(result): resolve websocket disconnection on page reload
```

### Descripción del PR

Cada PR debe incluir la siguiente información:
```markdown
## ¿Qué hace este PR?
Descripción clara y concisa del cambio realizado.

## ¿Por qué es necesario?
Explicación del problema que resuelve o la necesidad que cubre.

## ¿Cómo se probó?
Describir cómo se verificó que el cambio funciona correctamente.
- [ ] Tests unitarios pasan
- [ ] Probado localmente
- [ ] Sin errores en los logs

## Cambios principales
- Archivo 1: descripción del cambio
- Archivo 2: descripción del cambio

## Screenshots (si aplica)
Capturas de pantalla si hay cambios visuales.
```

### Tamaño de un PR

Un PR debe ser lo más pequeño posible. Un PR grande es difícil de revisar
y tiene más probabilidad de introducir errores.

| Tamaño | Líneas cambiadas | Evaluación |
|--------|-----------------|------------|
| Ideal | < 200 líneas | Fácil de revisar |
| Aceptable | 200 - 400 líneas | Revisión cuidadosa |
| Problemático | > 400 líneas | Dividir en PRs más pequeños |

Si una funcionalidad es muy grande, divídela en varios PRs pequeños
que puedan mergearse de forma independiente.

### Proceso de revisión

**Para el autor del PR:**
- Asignar al menos un revisor del equipo.
- Responder todos los comentarios, ya sea implementando el cambio
  sugerido o explicando por qué no aplica.
- No hacer merge sin la aprobación requerida.
- No ignorar comentarios sin responder.

**Para el revisor:**
- Revisar el PR dentro de las **24 horas** de haber sido asignado.
- Revisar que el código es legible y sigue los estándares del proyecto.
- Verificar que los tests cubren los casos importantes.
- Usar comentarios constructivos: sugerir, no exigir.
- Aprobar cuando el código cumple los estándares, aunque no sea
  exactamente como uno lo hubiera escrito.

### Tipos de comentarios en una revisión
```
# Comentario bloqueante (debe resolverse antes del merge)
MUST: Este método no cierra la conexión a la base de datos,
puede causar memory leak en producción.

# Sugerencia (no bloquea el merge)
SUGGEST: Podrías extraer esta lógica a una función separada
para mejorar la legibilidad.

# Pregunta (no bloquea el merge)
QUESTION: ¿Por qué usas un timeout de 30 segundos aquí?
¿Hay algún requisito específico?
```

---

## 8. Reglas de protección de la rama `main`

Las siguientes reglas están configuradas en **GitHub Settings → Branches**
para la rama `main` y no pueden ser desactivadas por ningún miembro del equipo:

| Regla | Configuración |
|-------|--------------|
| Require a pull request before merging | ✅ Activa |
| Required number of approvals | 1 aprobación mínima |
| Require status checks to pass before merging | ✅ Activa |
| Do not allow bypassing the above settings | ✅ Activa |

**¿Qué significa cada regla?**

- **Require a pull request:** Nadie puede hacer `git push origin main`
  directamente. GitHub lo rechaza automáticamente.
- **1 aprobación mínima:** El autor no puede aprobar su propio PR.
  Siempre necesita que otra persona lo revise.
- **Dismiss stale approvals:** Si alguien aprueba un PR y luego
  el autor hace más cambios, la aprobación se invalida y hay que
  volver a aprobar. Evita que se cuelen cambios después de la revisión.
- **Status checks:** El pipeline de CI (build + tests) debe estar
  en verde antes de permitir el merge.
- **Up to date:** La rama debe estar sincronizada con `main` antes
  de hacer merge. Evita que código desactualizado entre a producción.
- **No bypassing:** Estas reglas aplican incluso para los administradores
  del repositorio. Nadie está por encima del proceso.

---

## 9. Manejo de conflictos

Los conflictos ocurren cuando dos personas modificaron el mismo archivo
en la misma área. Son normales y no indican un error del proceso.

### Cómo resolver un conflicto
```bash
# 1. Actualizar main en tu máquina
git fetch origin

# 2. Desde tu rama, hacer rebase contra main actualizado
git rebase origin/main

# 3. Git pausará en cada conflicto y mostrará algo como:
<<<<<<< HEAD (tu cambio)
    fmt.Println("versión tuya")
=======
    fmt.Println("versión de main")
>>>>>>> origin/main

# 4. Editar el archivo manualmente, elegir qué conservar,
#    y eliminar los marcadores <<<, ===, >>>

# 5. Marcar el conflicto como resuelto
git add worker/main.go

# 6. Continuar el rebase
git rebase --continue

# 7. Subir los cambios (requiere --force-with-lease por el rebase)
git push --force-with-lease
```

### Cómo prevenir conflictos

- Hacer commits y push frecuentes para que el equipo vea en qué
  estás trabajando.
- Sincronizar tu rama con `main` cada día si el PR tarda varios días.
- Coordinar con el equipo cuando vayas a hacer cambios grandes en
  archivos que otros también están tocando.
- Mantener los PRs pequeños — menos cambios significa menos posibilidad
  de conflicto.

---

## 10. Manejo de bugs urgentes en producción

Si se detecta un bug crítico en producción que necesita solución inmediata,
el proceso es el mismo que para cualquier otro cambio — no hay excepciones
al flujo. La diferencia es la velocidad y la prioridad:
```bash
# 1. Crear rama desde main actualizado
git checkout main
git pull origin main
git checkout -b fix/descripcion-del-bug-critico

# 2. Aplicar el fix

# 3. Commit
git commit -m "fix(worker): resolve critical null pointer on empty kafka message"

# 4. Push y abrir PR inmediatamente con etiqueta "urgent"
git push -u origin fix/descripcion-del-bug-critico
```

En el PR se agrega la etiqueta **urgent** en GitHub y se notifica
al equipo por el canal de comunicación del proyecto para agilizar
la revisión. El revisor debe priorizar este PR sobre otras tareas.

**Lo que NO se hace aunque sea urgente:**
- Push directo a `main`.
- Saltarse la revisión.
- Desactivar las branch protection rules temporalmente.

