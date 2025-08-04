// Liste des microservices � g�rer dans le pipeline
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
//                     // V�rifier si le workspace est vide apr�s le checkout
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
//                         // Si le workspace est vide, on d�ploie tout
                        changedServices = microservices
//                         echo "Workspace vide d�tect� : tous les services seront d�ploy�s"
//                     } else {
//                         // R�cup�rer le hash du dernier build r�ussi
//                         def lastSuccessfulCommit = ""
//                         def previousBuild = currentBuild.previousSuccessfulBuild
//                         if (previousBuild) {
//                             lastSuccessfulCommit = previousBuild.rawBuild.getEnvironment().get('GIT_COMMIT')
//                         }
//
//                         for (service in microservices) {
//                             if (!lastSuccessfulCommit) {
//                                 // Premi�re ex�cution ou pas de build pr�c�dent r�ussi
//                                 changedServices.add(service)
//                             } else {
//                                 // V�rifier les modifications entre le dernier build r�ussi et maintenant
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
//                         // Si aucun service n'a �t� modifi�, on construit tous les services
//                         if (changedServices.isEmpty()) {
//                             changedServices = microservices
//                             echo "Aucun changement d�tect� : tous les services seront d�ploy�s"
//                         }
//                     }
//
//                     // Stockage des services modifi�s dans une variable d'environnement
//                     env.CHANGES = changedServices.join(',')
//                     echo "Services � d�ploy�s : ${env.CHANGES}"
                }
            }
        }

        stage('Project compilation') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            // Compilation diff�rente selon le type de projet
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

                        // Cr�ation du dossier scout-report s'il n'existe pas
                        powershell '''
                            if (!(Test-Path "scout-report")) {
                                New-Item -ItemType Directory -Force -Path "scout-report"
                            }
                        '''

                    }

                    // Analyse des d�pendances pour chaque image
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aper�u rapide et g�n�ral des vuln�rabilit�s
                            docker scout quickview ${imageTag}

                            # Analyse d�taill�e des CVEs et ajout dans un rapport
                            docker scout cves ${imageTag} --exit-code --only-severity critical,high > scout-report/${service}.txt

                            # Ajout des recommandations au rapport
                            docker scout recommendations ${imageTag} --only-severity critical,high >> scout-report/${service}.txt

                            # Arr�t du pipeline si une vuln�rabilit�s critique ou �lev�e est d�tect�e
                            docker scout cves ${imageTag} --exit-code --only-severity critical,high
                            if ($LASTEXITCODE -ne 0) {
                                Write-Output "[ERROR] Vuln�rabilit�s critiques d�tect�es dans ${imageTag}"
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
                    // Mise � jour des versions d'images (tag) dans le fichier docker-compose
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            powershell """
                                powershell -Command "(Get-Content ..\\docker-compose.yml) -replace '${service}:latest', '${imageTag}' | Set-Content ..\\docker-compose.yml"
                            """
                        }
                    }

                    // Arr�t des services existants
                    powershell 'docker-compose -H tcp://localhost:2375 down'

                    // D�marrage des services avec docker-compose
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

                echo """/!\\ ALERTE DE S�CURIT� /!\\
                Pipeline : ${jobName}
                Build : #${buildNumber}
                Status : �CHEC
                Raison : Des vuln�rabilit�s critiques ou �lev�es ont �t� d�tect�es
                URL du build : ${buildUrl}

                Veuillez consulter les rapports de s�curit� dans le dossier 'scout-report' pour plus de d�tails.
                """
            }
        }
        success {
            echo "? Pipeline ex�cut� avec succ�s - Aucune vuln�rabilit�s critique ou �lev�e d�tect�e"
        }
    }
}