// Recherche les dossiers dans le projet
def microservices = [
    'account-service',
    'angular-front-end',
    'customer-service',
    'config-service',
    'discovery-service',
    'gateway-service'

]

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
        // stage('Clean Workspace') {
        //     steps {
        //         cleanWs()
        //     }
        // }

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
                            if (service == 'angular-front-end') {
                                powershell '''
                                    npm install
                                    npm install @angular/cli@latest
                                    npm run build
                                '''
                            } else {
                                powershell 'mvn -B clean package -DskipTests'
                            }
                        }
                    }
                }
            }
        }

        stage('Build images') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')

                    // Construction des images Docker
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            powershell "docker -H tcp://localhost:2375 build -t ${imageTag} ."
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

                            # Installation de Docker Scout via Docker CLI
                            docker extension install docker/scout-extension
                        '''
                    }

                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aperçu rapide et général des vulnérabilités
                            docker scout quickview ${imageTag}

                            # Analyse détaillée des CVEs
                            docker scout cves ${imageTag} --exit-code --only-severity critical

                            # Génération du rapport
                            docker scout report ${imageTag} > scout-report-${service}.txt

                            # Recommendation pour les étapes de remédiation
                            docker scout recommandations ${imageTag}
                        """
                    }
                }
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')

                    // Mise à jour des tags d'images dans docker-compose.yml
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
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