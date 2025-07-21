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
        DOCKER_TLS_VERIFY = "1"
        DOCKER_HOST = "tcp://127.0.0.1:57773"
        DOCKER_CERT_PATH = "C:\\Users\\apayet\\.minikube\\certs"
        MINIKUBE_ACTIVE_DOCKERD = "minikube"
        DOCKER_REGISTRY = 'localhost:5000'
    }

    stages {
        stage('Checking Docker environment') {
            steps {
                script {
                    env.DOCKER_ENV_CONFIGURED = "false"

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
                            Write-Host "Configuration de l'environnement Docker..."
                            minikube -p minikube docker-env --shell powershell | Invoke-Expression
                            if ($?) {
                                Write-Output "true"
                            } else {
                                Write-Output "false"
                            }
                        ''',
                        returnStdout: true
                    ).trim()

                    echo "Résultat de la configuration Docker: ${dockerEnvSetup}"

                    if (dockerEnvSetup == "true") {
                        env.DOCKER_ENV_CONFIGURED = true
                        powershell 'Write-Host "Docker Env Configured: $env:DOCKER_ENV_CONFIGURED"'
                        echo "Configuration Docker réussie"
                    } else {
                        error "Échec de la configuration de l'environnement Docker"
                    }

                    // Vérifier les variables d'environnement
                    powershell '''
                        Write-Host "=== Vérification de l'environnement Docker ==="
                        Write-Host "Docker Host: $env:DOCKER_HOST"
                        Write-Host "Docker Cert Path: $env:DOCKER_CERT_PATH"
                        Write-Host "Docker TLS Verify: $env:DOCKER_TLS_VERIFY"
                        Write-Host "Minikube Active Dockerd: $env:MINIKUBE_ACTIVE_DOCKERD"
                        Write-Host "Docker Registry: $env:DOCKER_REGISTRY"
                        Write-Host "Docker Env Configured: $env:DOCKER_ENV_CONFIGURED"
                        Write-Host "========================================"
                    '''
                    echo "The Docker Env Configured: ${env.DOCKER_ENV_CONFIGURED}"
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
                        // Si aucun changement spécifique, construire tous les services
                        changedServices = microservices
                    }

                    // Stocker la liste comme une chaîne séparée par des virgules dans env.CHANGES
                    env.CHANGES = changedServices.join(',')
                }
            }
        }

//         stage('Build Projects') {
//             steps {
//                 script {
//                     def servicesList = env.CHANGES.split(',')
//                     for (service in servicesList) {
//                         dir(service) {
//                             powershell 'mvn -B clean package -DskipTests'
//                         }
//                     }
//                 }
//             }
//         }
//
//         stage('Build & Push Images Docker') {
//             when {
//                 expression {
//                     echo "Vérification de DOCKER_ENV_CONFIGURED: ${env.DOCKER_ENV_CONFIGURED}"
//                     return env.DOCKER_ENV_CONFIGURED == 'true'
//                 }
//             }
//             steps {
//                 script {
//                     echo "Début de la construction des images Docker"
//                     def servicesList = env.CHANGES.split(',')
//                     for (service in servicesList) {
//                         dir(service) {
//                             def imageTag = "${DOCKER_REGISTRY}/${service}:${env.BUILD_NUMBER}"
//                             echo "Construction de l'image: ${imageTag}"
//                             powershell "docker build -t ${imageTag} ."
//                             echo "Push de l'image: ${imageTag}"
//                             powershell "docker push ${imageTag}"
//                         }
//                     }
//                 }
//             }
//         }

        stage('Helm Deployment') {
            when {
                expression {
                    echo "Vérification de DOCKER_ENV_CONFIGURED pour Helm: ${env.DOCKER_ENV_CONFIGURED}"
                    return env.DOCKER_ENV_CONFIGURED == 'true'
                }
            }
            steps {
                script {
                    echo "Installation de Helm"
                    powershell '''
                        Invoke-WebRequest -Uri "https://get.helm.sh/helm-v3.8.0-windows-amd64.zip" -OutFile "helm-v3.8.0-windows-amd64.zip"
                        Expand-Archive -Path "helm-v3.8.0-windows-amd64.zip" -DestinationPath "C:\\Users\\apayet\\IdeaProjects\\helm"
                        $env:PATH += ";C:\\Users\\apayet\\IdeaProjects\\helm\\windows-amd64"
                    '''

                    echo "Vérifier que Helm est accessible"
                    powershell "helm version"

                    echo "Début du déploiement Helm"
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            echo "Déploiement de ${service}"
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
        always {
            echo "État final de DOCKER_ENV_CONFIGURED: ${env.DOCKER_ENV_CONFIGURED}"
        }
    }
}
