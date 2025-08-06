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
        DOCKER_HOST = 'tcp://localhost:2375'
        DOCKER_SCOUT_TEMP_DIR = 'C:\\WINDOWS\\SystemTemp\\docker-scout'
    }

    stages {
        stage('Preparing the environment') {
            steps {
                script {
                    powershell '''
                        if (Test-Path $env:DOCKER_SCOUT_TEMP_DIR) {
                            Remove-Item -Path $env:DOCKER_SCOUT_TEMP_DIR -Recurse -Force -ErrorAction SilentlyContinue
                        }
                    '''
                }
            }
        }

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
                        echo "Aucun changement détecté : tous les services seront déployés"
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
                            powershell "docker build -t ${imageTag} ."
                        }
                    }
                }
            }
        }

        stage('Docker Authentification') {
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
                    }
                }
            }
        }

        stage('Docker Scout') {
            steps {
                script {
                    // Création du dossier pour les rapport s'il n'existe pas
                    powershell '''
                        if (!(Test-Path "scout-report")) {
                            New-Item -ItemType Directory -Force -Path "scout-report"
                        }
                    '''

                    // Analyse des dépendances pour chaque image
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aperçu rapide et général des vulnérabilités
                            docker scout quickview ${imageTag} > scout-report/${service}.txt

                            # Analyse détaillée des CVEs et ajout dans un rapport
                            docker scout cves ${imageTag} --exit-code --only-severity critical >> scout-report/${service}.txt

                            # Nettoyage des fichiers temporaires après chaque analyse
                            if (Test-Path "$env:DOCKER_SCOUT_TEMP_DIR") {
                                Remove-Item -Path $env:DOCKER_SCOUT_TEMP_DIR -Recurse -Force -ErrorAction SilentlyContinue
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
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            powershell -Command "(Get-Content docker-compose.yml) -replace '${service}:latest', '${imageTag}' | Set-Content docker-compose.yml"
                        """
                    }
                    // Si servicesList contient tous les microservices, on fait un déploiement complet
                    if (servicesList.sort() == microservices.sort()) {
                        echo "Déploiement complet de tous les services"
                        powershell '''
                            docker compose down
                            docker compose up -d
                        '''
                    } else {
                        echo "Déploiement sélectif des services modifiés : ${servicesList}"
                        // Mise à jour des tags dans le fichier docker-compose et redémarrage des services modifiés
                        for (service in servicesList) {
                            powershell """
                                docker compose stop ${service}
                                docker compose up -d ${service}
                            """
                        }
                    }
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
                def buildLog = currentBuild.rawBuild.getLog(2000).join('\n')

                if (buildLog.contains('docker scout cves')) {
                    echo """/!\\ ALERTE DE SÉCURITÉ /!\\
                    Pipeline : ${jobName}
                    Build : #${buildNumber}
                    Status : ÉCHEC
                    Raison : Des vulnérabilités critiques ont été détectées
                    URL du build : ${buildUrl}

                    Veuillez consulter les rapports de sécurité dans le dossier 'scout-report' pour plus de détails.
                    """
                } else if (buildLog.toLowerCase().contains('error logging in') ||
                           buildLog.contains('unauthorized') ||
                           buildLog.contains('authentification required') ||
                           buildLog.contains('access denied') ||
                           buildLog.contains('permission denied')) {
                    echo """/!\\ ERREUR D'AUTHENTIFICATION DOCKER /!\\
                    Pipeline : ${jobName}
                    Build : #${buildNumber}
                    Status : ÉCHEC
                    Raison : Des vulnérabilités critiques ont été détectées
                    URL du build : ${buildUrl}

                    Actions recommandées :
                    1. Vérifier que le credential 'DOCKER_PAT' est correctement configuré dans Jenkins
                    2. Vérifier que le token Docker Hub n'a pas expiré
                    3. Vérifier que l'utilisateur '${env.DOCKER_HUB_USER}' a les permissions necéssaires
                    4. Vérifier que Docker Desktop est bien démarré et accessible
                    """
                } else {
                    echo """Pipeline : ${jobName}
                    Build : #${buildNumber}
                    Status : ÉCHEC
                    Raison : Erreur technique dans le pipeline
                    URL du build : ${buildUrl}
                    """
                }
            }
        }
        always {
            script {
                // Nettoyage final des fichiers temporaires
                powershell '''
                    if (Test-Path $env:DOCKER_SCOUT_TEMP_DIR) {
                        try {
                            Remove-Item -Path $env:DOCKER_SCOUT_TEMP_DIR -Recurse -Force -ErrorAction Stop
                            Write-Host "Nettoyage des fichiers temporaires Docker Scout effectué avec succès"
                        } catch {
                            Write-Warning "Impossible de nettoyer complètement $env:DOCKER_SCOUT_TEMP_DIR : $($_.Exception.Message)"
                        }
                    } else {
                        Write-Host "Aucun fichier temporaire Docker Scout à nettoyer"
                    }
                '''
            }
        }
        success {
            echo "Pipeline exécuté avec succès"
        }
    }
}