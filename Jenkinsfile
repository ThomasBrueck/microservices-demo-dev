pipeline {
    agent any

    environment {
        // Azure Container Registry
        ACR_NAME        = 'turegistro'
        ACR_LOGIN_SERVER = "${ACR_NAME}.azurecr.io"
        ACR_CREDENTIALS = credentials('acr-credentials')

        // VM App de Azure (Private Subnet)
        VM_APP_HOST = credentials('vm-app-host')
        VM_APP_USER = 'azureuser'

        // Tag de imagen usa el numero de build de Jenkins
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    options {
        // Si el pipeline tarda mas de 30 minutos, falla automaticamente
        timeout(time: 30, unit: 'MINUTES')
        // Mantiene solo los ultimos 10 builds en el historial
        buildDiscarder(logRotator(numToKeepStr: '10'))
        // No permite correr dos builds del mismo pipeline al mismo tiempo
        disableConcurrentBuilds()
    }

    stages {

        // ─── STAGE 1: CHECKOUT ───────────────────────────────────────
        stage('Checkout') {
            steps {
                echo "Clonando repositorio en rama: ${env.BRANCH_NAME}"
                checkout scm
                // Muestra el ultimo commit para trazabilidad
                sh 'git log -1 --oneline'
            }
        }

        // ─── STAGE 2: BUILD & TEST (paralelo por microservicio) ──────
        stage('Build & Test') {
            parallel {

                stage('vote - Java') {
                    steps {
                        script {
                            echo "=== Compilando y testeando vote (Java) ==="
                            sh '''
                                chmod +x jenkins/scripts/build-vote.sh
                                jenkins/scripts/build-vote.sh
                            '''
                        }
                    }
                    post {
                        always {
                            // Publica reporte de tests JUnit en Jenkins UI
                            junit allowEmptyResults: true,
                                  testResults: 'vote/target/surefire-reports/*.xml'
                        }
                    }
                }

                stage('worker - Go') {
                    steps {
                        script {
                            echo "=== Compilando y testeando worker (Go) ==="
                            sh '''
                                chmod +x jenkins/scripts/build-worker.sh
                                jenkins/scripts/build-worker.sh
                            '''
                        }
                    }
                }

                stage('result - Node.js') {
                    steps {
                        script {
                            echo "=== Instalando dependencias y testeando result (Node.js) ==="
                            sh '''
                                chmod +x jenkins/scripts/build-result.sh
                                jenkins/scripts/build-result.sh
                            '''
                        }
                    }
                }
            }
        }

        // ─── STAGE 3: DOCKER BUILD ───────────────────────────────────
        stage('Docker Build') {
            steps {
                echo "=== Construyendo imagenes Docker para los 3 microservicios ==="
                sh '''
                    chmod +x jenkins/scripts/docker-build-push.sh
                    jenkins/scripts/docker-build-push.sh build ${IMAGE_TAG} ${ACR_LOGIN_SERVER}
                '''
            }
        }

        // ─── STAGE 4: DOCKER PUSH A ACR ──────────────────────────────
        stage('Docker Push') {
            steps {
                echo "=== Subiendo imagenes al Azure Container Registry ==="
                sh '''
                    # Login al ACR usando las credenciales configuradas en Jenkins
                    echo ${ACR_CREDENTIALS_PSW} | docker login ${ACR_LOGIN_SERVER} \
                        -u ${ACR_CREDENTIALS_USR} \
                        --password-stdin

                    chmod +x jenkins/scripts/docker-build-push.sh
                    jenkins/scripts/docker-build-push.sh push ${IMAGE_TAG} ${ACR_LOGIN_SERVER}
                '''
            }
        }

        // ─── STAGE 5: DEPLOY A STAGING ───────────────────────────────
        stage('Deploy') {
            steps {
                echo "=== Desplegando en VM App via SSH ==="
                sshagent(['vm-app-ssh-key']) {
                    sh '''
                        chmod +x jenkins/scripts/deploy.sh
                        jenkins/scripts/deploy.sh \
                            ${VM_APP_USER} \
                            ${VM_APP_HOST} \
                            ${ACR_LOGIN_SERVER} \
                            ${IMAGE_TAG} \
                            ${ACR_NAME}
                    '''
                }
            }
        }

        // ─── STAGE 6: VERIFICACION POST-DEPLOY ───────────────────────
        stage('Health Check') {
            steps {
                echo "=== Verificando que los servicios esten respondiendo ==="
                sh '''
                    # Espera 15 segundos para que los contenedores terminen de iniciar
                    sleep 15

                    # Verifica que vote responde en su puerto
                    curl --fail --silent --max-time 10 \
                        http://${VM_APP_HOST}:8080/actuator/health \
                        && echo "vote: OK" \
                        || echo "vote: NO RESPONDE"

                    # Verifica que result responde en su puerto
                    curl --fail --silent --max-time 10 \
                        http://${VM_APP_HOST}:5000 \
                        && echo "result: OK" \
                        || echo "result: NO RESPONDE"
                '''
            }
        }

        // ─── STAGE 7: APROBACION MANUAL PARA PRODUCCION ──────────────
        stage('Approval') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    input message: '¿Aprobar despliegue a produccion?',
                          ok: 'Aprobar',
                          submitter: 'admin,devops-team'
                }
            }
        }

        // ─── STAGE 8: DEPLOY A PRODUCCION ────────────────────────────
        stage('Deploy Produccion') {
            steps {
                echo "=== Despliegue aprobado. Ejecutando en produccion ==="
                sshagent(['vm-app-ssh-key']) {
                    sh '''
                        jenkins/scripts/deploy.sh \
                            ${VM_APP_USER} \
                            ${VM_APP_HOST} \
                            ${ACR_LOGIN_SERVER} \
                            ${IMAGE_TAG}
                    '''
                }
            }
        }
    }

    // ─── POST: acciones al finalizar el pipeline ─────────────────────
    post {
        success {
            echo """
            ============================================
            Pipeline completado exitosamente
            Build: ${BUILD_NUMBER}
            Imagenes desplegadas con tag: ${IMAGE_TAG}
            ============================================
            """
        }
        failure {
            echo """
            ============================================
            Pipeline FALLIDO en build: ${BUILD_NUMBER}
            Revisa los logs para identificar el problema
            ============================================
            """
        }
        always {
            // Limpia las imagenes Docker locales para no llenar el disco de Jenkins
            sh '''
                docker rmi ${ACR_LOGIN_SERVER}/vote:${IMAGE_TAG} || true
                docker rmi ${ACR_LOGIN_SERVER}/worker:${IMAGE_TAG} || true
                docker rmi ${ACR_LOGIN_SERVER}/result:${IMAGE_TAG} || true
            '''
        }
    }
}