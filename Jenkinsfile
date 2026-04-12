pipeline {
    agent any

    environment {
        // Azure Container Registry
        ACR_NAME         = 'acrapp1'
        ACR_LOGIN_SERVER = "${ACR_NAME}.azurecr.io"
        ACR_CREDENTIALS  = credentials('acr-credentials')

        // Tag de imagen usa el numero de build de Jenkins para trazabilidad
        IMAGE_TAG = "${BUILD_NUMBER}"
    }

    options {
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {
        // ─── STAGE 1: CHECKOUT ───────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        // ─── STAGE 2: BUILD & TEST (Paralelo) ────────────────────────
        stage('Build & Test') {
            parallel {
                stage('vote - Java') {
                    steps {
                        sh 'chmod +x jenkins/scripts/build-vote.sh && jenkins/scripts/build-vote.sh'
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'vote/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('worker - Go') {
                    steps {
                        sh 'chmod +x jenkins/scripts/build-worker.sh && jenkins/scripts/build-worker.sh'
                    }
                }
                stage('result - Node.js') {
                    steps {
                        sh 'chmod +x jenkins/scripts/build-result.sh && jenkins/scripts/build-result.sh'
                    }
                }
            }
        }

        // ─── STAGE 3: DOCKER BUILD (Doble Tag) ───────────────────────
        stage('Docker Build') {
            steps {
                echo "=== Construyendo imágenes con tags: ${IMAGE_TAG} y latest ==="
                sh """
                    chmod +x jenkins/scripts/docker-build-push.sh
                    
                    # Construimos la versión específica del build
                    jenkins/scripts/docker-build-push.sh build ${IMAGE_TAG} ${ACR_LOGIN_SERVER}
                    
                    # Construimos/Taggeamos como latest para que Ops siempre tenga lo último
                    jenkins/scripts/docker-build-push.sh build latest ${ACR_LOGIN_SERVER}
                """
            }
        }

        // ─── STAGE 4: DOCKER PUSH ────────────────────────────────────
        stage('Docker Push') {
            steps {
                echo "=== Subiendo imágenes al ACR ==="
                sh """
                    echo ${ACR_CREDENTIALS_PSW} | docker login ${ACR_LOGIN_SERVER} \
                        -u ${ACR_CREDENTIALS_USR} --password-stdin

                    chmod +x jenkins/scripts/docker-build-push.sh
                    
                    # Subimos ambas versiones al registro
                    jenkins/scripts/docker-build-push.sh push ${IMAGE_TAG} ${ACR_LOGIN_SERVER}
                    jenkins/scripts/docker-build-push.sh push latest ${ACR_LOGIN_SERVER}
                """
            }
        }
    }

    // ─── POST: EL PUENTE HACIA OPS ───────────────────────────────────
    post {
        success {
            echo "✅ CI completado con éxito. Disparando Pipeline de Despliegue (Ops)..."
            
            // Encadenamiento: Lanza el job de Ops automáticamente.
            // 'wait: false' permite que este pipeline termine sin esperar al de Ops.
            build job: 'microservices-demo-ops-pipeline', wait: false
        }
        
        failure {
            echo "❌ El pipeline de DEV falló. No se realizará el despliegue."
        }

        always {
            // Limpieza de imágenes locales para liberar espacio en el servidor de Jenkins
            sh """
                docker rmi ${ACR_LOGIN_SERVER}/vote:${IMAGE_TAG} ${ACR_LOGIN_SERVER}/vote:latest || true
                docker rmi ${ACR_LOGIN_SERVER}/worker:${IMAGE_TAG} ${ACR_LOGIN_SERVER}/worker:latest || true
                docker rmi ${ACR_LOGIN_SERVER}/result:${IMAGE_TAG} ${ACR_LOGIN_SERVER}/result:latest || true
            """
        }
    }
}