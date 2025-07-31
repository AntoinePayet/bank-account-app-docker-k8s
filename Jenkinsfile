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
        DOCKER_HUB_PAT = credentials('DOCKER_PAT')
        DOCKER_HUB_USER = 'antoinepayet'
    }

    stages {
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

        stage('Project compilation') {
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

        stage('Docker Scout') {
            steps {
                script {
                    // Connexion à Docker Hub de manière sécurisée
                    withCredentials([string(credentialsId: 'DOCKER_PAT', variable: 'DOCKER_HUB_PAT')]) {
                        powershell '''
                            $password = $env:DOCKER_HUB_PAT
                            $username = $env:DOCKER_HUB_USER
                            docker login -u $username -p $password
                        '''
                    }

                    def servicesList = env.CHANGES.split(',')

                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        // Analyze image for CVEs
                        powershell "docker-scout cves ${imageTag} --exit-code --only-severity critical,high"

                        // Get recommendations for remediation steps
                        powershell "docker-scout recommandations ${imageTag}"
                    }
                }
            }
        }

        stage('Build and Deploy with Docker Compose') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')

                    // Construction des images Docker pour les services modifiés
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            powershell "docker -H tcp://localhost:2375 build -t ${imageTag} ."

                            // Mise à jour du tag d'image dans docker-compose.yml
                            powershell """
                                powershell -Command "(Get-Content ..\\docker-compose.yml) -replace '${service}:latest', '${imageTag}' | Set-Content ..\\docker-compose.yml"
                            """
                        }
                    }

                    // Arrêt des services existants
                    powershell 'docker-compose -H tcp://localhost:2375 down'

                    // Démarrage des services avec docker-compose
                    powershell 'docker-compose -H tcp://localhost:2375 up -d --build'
                }
            }
        }
    }
}