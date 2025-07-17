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

    DOCKER_REGISTRY = 'localhost:5000'

    stages {
        stage('Clone Repository') {
            steps {
                git 'https://github.com/AntoinePayet/bank-account-app-docker-k8s.git'
            }
        }

        stage('Configuration Minikube') {
            steps {
                script {
                    bat 'minikube tunnel'
                }
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []
                    for (service in microservices) {
                        def changes = bat(
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
                            bat 'mvn -B clean package -DskipTests'
                        }
                    }
                }
            }
        }

        stage('Build et Push Images Docker') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${DOCKER_REGISTRY}/${service}:${env.BUILD_NUMBER}"
                            // Configuration de l'environnement Docker pour Minikube
                            bat 'minikube -p minikube docker-env --shell powershell | Invoke-Expression'
                            bat "docker build -t ${imageTag} ."
                            bat "docker push ${imageTag}"
                        }
                    }
                }
            }
        }

        stage('Déploiement Helm') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        // Mise à jour ou installation des charts Helm
                        dir(service) {
                            bat "helm upgrade --install ${service} .\\${service}\\ ."
                        }
                    }
                }
            }
        }
    }
}
