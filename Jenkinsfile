def microservices = [
    'account-service',
    'customer-service',
    'config-service',
    'discovery-service',
    'gateway-service'
]

def getServicePort(service) {
    def ports = [
        'account-service': '8082',
        'customer-service': '8081',
        'config-service': '9999',
        'discovery-service': '8761',
        'gateway-service': '8888'
    ]
    return ports[service]
}

pipeline {
    agent any

    tools {
        maven 'maven-3.9.10'
    }

    environment {
        DOCKER_HOST = "tcp://127.0.0.0:62099"
        DOCKER_CERT_PATH = "C:\\Users\\apayet\\.minikube\\certs"
        MINIKUBE_ACTIVE_DOCKERD = "minikube"
        DOCKER_TLS_VERIFY = "1"
        DOCKER_REGISTRY = 'localhost:5000'
        DOCKER_ENV_CONFIGURED = 'false'
    }

    stages {
        stage('Clone Repository') {
            steps {
                git 'https://github.com/AntoinePayet/bank-account-app-docker-k8s.git'
            }
        }

        stage('Vérification de l\'environnement Docker') {
            steps {
                script {
                    try {
                        // Vérifier si Minikube est en cours d'exécution
                        def minikubeStatus = powershell(
                            script: 'minikube status',
                            returnStatus: true
                        )

                        if (minikubeStatus != 0) {
                            error "Minikube n'est pas en cours d'exécution"
                        }

                        // Configurer l'environnement Docker pour Minikube
                        def dockerEnvSetup = powershell(
                            script: '''
                                $dockerEnv = minikube -p minikube docker-env --shell powershell
                                if ($?) {
                                    $dockerEnv | Invoke-Expression
                                    Write-Output "true"
                                } else {
                                    Write-Output "false"
                                }
                            ''',
                            returnStdout: true
                        ).trim()

                        if (dockerEnvSetup == "true") {
                            env.DOCKER_ENV_CONFIGURED = 'true'
                            echo "Configuration Docker réussie"
                        } else {
                            error "Échec de la configuration de l'environnement Docker"
                        }

                        // Vérifier les variables d'environnement
                        powershell '''
                            Write-Host "Docker Host: $env:DOCKER_HOST"
                            Write-Host "Docker Cert Path: $env:DOCKER_CERT_PATH"
                            Write-Host "Docker TLS Verify: $env:DOCKER_TLS_VERIFY"
                            Write-Host "Minikube Active Dockerd: $env:MINIKUBE_ACTIVE_DOCKERD"
                            Write-Host "Docker Registry: $env:DOCKER_REGISTRY"
                        '''
                    } catch (Exception e) {
                        env.DOCKER_ENV_CONFIGURED = 'false'
                        error "Erreur lors de la configuration de l'environnement Docker: ${e.getMessage()}"
                    }
                }
            }
        }



        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []
                    for (service in microservices) {
                        def changes = powershell(
                            script: "git diff --name-only HEAD^..HEAD ${service}/",
                            returnStdout: true
                        ).trim()

                        if (changes) {
                            changedServices.add(service)
                        }
                    }

                    if (changedServices.isEmpty()) {
                        changedServices = microservices // Si aucun changement spécifique, construire tous les services
                    }

                    // Stocker la liste comme une chaîne séparée par des virgules dans env.CHANGES
                    env.CHANGES = changedServices.join(',')
                }
            }
        }

        stage('Build Projects') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            powershell 'mvn -B clean package -DskipTests'
                        }
                    }
                }
            }
        }

        stage('Build et Push Images Docker') {
            when {
               expression { env.DOCKER_ENV_CONFIGURED == 'true' }
            }
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${DOCKER_REGISTRY}/${service}:${env.BUILD_NUMBER}"
                            powershell "docker build -t ${imageTag} ."
                            powershell "docker push ${imageTag}"
                        }
                    }
                }
            }
        }

        stage('Déploiement Helm') {
            when {
               expression { env.DOCKER_ENV_CONFIGURED == 'true' }
            }
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            powershell "helm upgrade --install ${service} .\\${service}\\ ."
                        }
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                if (env.DOCKER_ENV_CONFIGURED == 'false') {
                    echo "Le pipeline a échoué en raison d'une mauvaise configuration de l'environnement Docker"
                }
            }
        }
    }
}
