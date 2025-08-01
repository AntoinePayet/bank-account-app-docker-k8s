// Liste des microservices à gérer dans le pipeline
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

    // Variables d'environnement globales
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
                        // Vérification des fichiers modifiés entre le commit actuel et le précédent
                        def changes = powershell(
                            script: "git diff --name-only HEAD^..HEAD ${service}/",
                            returnStdout: true
                        ).trim()

                        if (changes) {
                            changedServices.add(service)
                        }
                    }

                    // Si aucun service n'a été modifié, on construit tous les services
                    if (changedServices.isEmpty()) {
                        changedServices = microservices
                    }

                    // Stockage des services modifiés dans une variable d'environnement
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
                            // Compilation différente selon le type de projet
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
                    // Authentification Docker Hub pour utiliser Docker Scout
                    withCredentials([string(credentialsId: 'DOCKER_PAT', variable: 'DOCKER_HUB_PAT')]) {
                        powershell '''
                            $password = $env:DOCKER_HUB_PAT
                            $username = $env:DOCKER_HUB_USER
                            docker login -u $username -p $password

                            # Installation de Docker Scout via Docker CLI
                            docker extension install docker/scout-extension
                        '''
                    }

                    // Analyse des dépendances pour chaque image
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aperçu rapide et général des vulnérabilités
                            docker scout quickview ${imageTag}

                            # Analyse détaillée des CVEs
                            docker scout cves ${imageTag} --exit-code --only-severity critical,high

                            # Génération du rapport
                            docker scout report ${imageTag} > scout-report-${service}.txt

                            # Recommendation pour les étapes de remédiation
                            docker scout recommendations ${imageTag}
                        """
                    }
                }
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    // Mise à jour des versions d'images (tag) dans le fichier docker-compose
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