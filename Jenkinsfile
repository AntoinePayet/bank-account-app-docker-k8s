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
//         stage('Clean Workspace') {
//             steps {
//                 cleanWs()
//                 checkout scm
//             }
//         }

        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []
//                     // Vérifier si le workspace est vide après le checkout
//                     def workspaceEmpty = powershell(
//                         script: """
//                             if (-not (Test-Path -Path '*')) {
//                                 exit 1
//                             } else {
//                                 exit 0
//                             }
//                             """,
//                         returnStatus: true
//                     )
//
//                     if (workspaceEmpty) {
//                         // Si le workspace est vide, on déploie tout
                        changedServices = microservices
//                         echo "Workspace vide détecté : tous les services seront déployés"
//                     } else {
//                         // Récupérer le hash du dernier build réussi
//                         def lastSuccessfulCommit = ""
//                         def previousBuild = currentBuild.previousSuccessfulBuild
//                         if (previousBuild) {
//                             lastSuccessfulCommit = previousBuild.rawBuild.getEnvironment().get('GIT_COMMIT')
//                         }
//
//                         for (service in microservices) {
//                             if (!lastSuccessfulCommit) {
//                                 // Première exécution ou pas de build précédent réussi
//                                 changedServices.add(service)
//                             } else {
//                                 // Vérifier les modifications entre le dernier build réussi et maintenant
//                                 def changes = powershell(
//                                     script: "git diff --name-only ${lastSuccessfulCommit}..HEAD ${service}/",
//                                     returnStdout: true
//                                 ).trim()
//
//                                 if (changes) {
//                                     changedServices.add(service)
//                                 }
//                             }
//                         }
//                         // Si aucun service n'a été modifié, on construit tous les services
//                         if (changedServices.isEmpty()) {
//                             changedServices = microservices
//                             echo "Aucun changement détecté : tous les services seront déployés"
//                         }
//                     }
//
//                     // Stockage des services modifiés dans une variable d'environnement
//                     env.CHANGES = changedServices.join(',')
//                     echo "Services à déployés : ${env.CHANGES}"
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
                            docker extension install docker/scout-extension
                        '''

                        // Création du dossier scout-report s'il n'existe pas
                        powershell '''
                            if (!(Test-Path "scout-report")) {
                                New-Item -ItemType Directory -Force -Path "scout-report"
                            }
                        '''

                    }

                    // Analyse des dépendances pour chaque image
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aperçu rapide et général des vulnérabilités
                            docker scout quickview ${imageTag}

                            # Analyse détaillée des CVEs et ajout dans un rapport
                            docker scout cves ${imageTag} --exit-code --only-severity critical,high > scout-report/${service}.txt

                            # Ajout des recommandations au rapport
                            docker scout recommendations ${imageTag} --only-severity critical,high >> scout-report/${service}.txt

                            # Arrêt du pipeline si une vulnérabilités critique ou élevée est détectée
                            docker scout cves ${imageTag} --exit-code --only-severity critical,high
                            if ($LASTEXITCODE -ne 0) {
                                Write-Output "[ERROR] Vulnérabilités critiques détectées dans ${imageTag}"
                                exit 1
                            }
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
    post {
        failure {
            script {
                def buildUrl = env.BUILD_URL
                def jobName = env.JOB_NAME
                def buildNumber = env.BUILD_NUMBER

                echo """/!\\ ALERTE DE SÉCURITÉ /!\\
                Pipeline : ${jobName}
                Build : #${buildNumber}
                Status : ÉCHEC
                Raison : Des vulnérabilités critiques ou élevées ont été détectées
                URL du build : ${buildUrl}

                Veuillez consulter les rapports de sécurité dans le dossier 'scout-report' pour plus de détails.
                """
            }
        }
        success {
            echo "? Pipeline exécuté avec succès - Aucune vulnérabilités critique ou élevée détectée"
        }
    }
}