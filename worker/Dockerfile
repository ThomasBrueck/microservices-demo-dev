# --- ETAPA 1: Compilación (Build) ---
FROM golang:1.24.1-bookworm AS dev

COPY bashrc /root/.bashrc

WORKDIR /app

# Instalación de herramientas de desarrollo (se quedan en la etapa de build)
RUN go install github.com/codegangsta/gin@v0.0.0-20230218063734-2c98d96c9244 && \
    go install github.com/go-delve/delve/cmd/dlv@v1.24.0 && \
    go install golang.org/x/tools/gopls@v0.17.1 && \
    go install github.com/air-verse/air@v1.61.5

ADD go.mod ./
RUN go mod download

ADD . .
RUN go mod tidy

# Compilamos el binario estático
RUN CGO_ENABLED=0 GOOS=linux go build -v -o /usr/local/bin/worker main.go


# --- ETAPA 2: Ejecución (Runtime) - Opción A: Alpine ---
FROM alpine:3.19

# 1. Instalamos netcat (nc) para que tus scripts de espera funcionen
# 2. Instalamos ca-certificates (VITAL para conectar a Azure PostgreSQL y Event Hubs por SSL)
RUN apk add --no-cache netcat-openbsd ca-certificates

# Copiamos el binario desde la etapa anterior
COPY --from=dev /usr/local/bin/worker /usr/local/bin/worker

# Mantenemos tu punto de entrada original
ENTRYPOINT ["/usr/local/bin/worker"]