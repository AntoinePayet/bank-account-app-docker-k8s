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
        DOCKER_REGISTRY = '192.168.49.2:5000'
    }

    stages {
        stage('TEST MINIKUBE') {
            steps {
                script {
                    powershell "minikube start"
                    powershell "minikube ip"
                    powershell "minikube dashboard"
                    powershell "minikube stop"
                }
            }
        }


//         stage('Clone Repository') {
//             steps {
//                 git 'https://github.com/AntoinePayet/bank-account-app-docker-k8s.git'
//             }
//         }

//         stage('Detect Changes') {
//             steps {
//                 script {
//                     def changedServices = []
//                     for (service in microservices) {
//                         def changes = powershell(
//                             script: "git diff --name-only HEAD^..HEAD ${service}/",
//                             returnStdout: true
//                         ).trim()
//
//                         if (changes) {
//                             changedServices.add(service)
//                         }
//                     }
//
//                     if (changedServices.isEmpty()) {
//                         changedServices = microservices // Si aucun changement spécifique, construire tous les services
//                     }
//
//                     // Stocker la liste comme une chaîne séparée par des virgules dans env.CHANGES
//                     env.CHANGES = changedServices.join(',')
//                 }
//             }
//         }

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

//         stage('Build et Push Images Docker') {
//             steps {
//                 script {
//                     powershell '''
//                         # Vérifier si Minikube est en cours d'exécution
//                         $minikubeStatus = minikube status
//                         if ($LASTEXITCODE -ne 0) {
//                             Write-Error "Minikube n'est pas en cours d'exécution"
//                             exit 1
//                         }
//
//                         # Configurer l'environnement Docker pour Minikube
//                         $dockerEnv = minikube -p minikube docker-env --shell powershell
//                         if ($dockerEnv) {
//                             $dockerEnv | Invoke-Expression
//                         } else {
//                             throw "Erreur lors de la configuration de l'environnement Docker"
//                         }
//                     '''
//                     def servicesList = env.CHANGES.split(',')
//                     for (service in servicesList) {
//                         dir(service) {
//                             def imageTag = "${DOCKER_REGISTRY}/${service}:${env.BUILD_NUMBER}"
//                             powershell "docker build -t ${imageTag} ."
//                             powershell "docker push ${imageTag}"
//                         }
//                     }
//                 }
//             }
//         }

//         stage('Déploiement Helm') {
//             steps {
//                 script {
//                     def servicesList = env.CHANGES.split(',')
//                     for (service in servicesList) {
//                         // Mise à jour ou installation des charts Helm
//                         dir(service) {
//                             powershell "helm upgrade --install ${service} .\\${service}\\ ."
//                         }
//                     }
//                 }
//             }
//         }
    }
}
