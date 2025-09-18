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

    // Variables d'environnement globales accessibles dans tout le pipeline
    environment {
        // Jeton d'accès Docker Hub stocké dans Jenkins Crédentials
        DOCKER_HUB_PAT = credentials('DOCKER_PAT')
        // Nom d'utilisateur Docker Hub (non sensible)
        DOCKER_HUB_USER = 'antoinepayet'
        // Hôte Docker (Docker Desktop exposé via le daemon TCP)
//         DOCKER_HOST = 'tcp://localhost:2375'
        // Dossier temporaire pour Docker Scout (nettoyé régulièrement)
        DOCKER_SCOUT_TEMP_DIR = 'C:\\WINDOWS\\SystemTemp\\docker-scout'
    }

    stages {
        // 1) Préparation de l'environnement d'exécution
        stage('Preparing the environment') {
            steps {
                script {
                    // Nettoyage préventif du répertoire temporaire Docker Scout
                    powershell '''
                        if (Test-Path $env:DOCKER_SCOUT_TEMP_DIR) {
                            Remove-Item -Path $env:DOCKER_SCOUT_TEMP_DIR -Recurse -Force -ErrorAction SilentlyContinue
                        }
                    '''
                }
            }
        }

        // 2) Détection des changements pour construire/déployer uniquement les services modifiés
        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []
                    for (service in microservices) {
                        // Compare les fichiers modifiés entre le dernier commit et l'actuel pour chaque service
                        def changes = powershell(
                            script: "git diff --name-only HEAD^..HEAD ${service}/",
                            returnStdout: true
                        ).trim()

                        // Si des changements sont détectés dans le dossier du service, on l'ajoute à la liste
                        if (changes) {
                            changedServices.add(service)
                        }
                    }

                    // Inclure les services qui n'ont jamais été déployés (aucun conteneur créé)
                    for (service in microservices) {
                        def hasAnyContainer = powershell (
                            script: "docker compose ps -a -q ${service}",
                            returnStdout: true
                        ).trim()
                        if (!hasAnyContainer){
                            echo "Service '${service}' sans conteneur existant détecté : inclusion pour déploiement initial"
                            changedServices.add(service)
                        }
                    }

                    // Détection du premier run: pas de build précédent réussi ou build #1 -> déploiement complet
                    def isFirstRun = (currentBuild?.previousSuccessfulBuild == null && env.BUILD_NUMBER == '1')
                    if (isFirstRun) {
                        echo "Premier lancement du pipeline détecté : déploiement complet de tous les services"
                        changedServices = microservices
                    }

                    // Si aucun service n'a été modifié ou marqué comme "non déployé", on traite tous les services (déploiement complet)
                    changedServices = changedServices.unique()
                    if (changedServices.isEmpty()) {
                        changedServices = microservices
                        echo "Aucun changement détecté : tous les services seront déployés"
                    }

                    // Partage la liste des services à traiter avec les stages suivants
                    env.CHANGES = changedServices.join(',')
                    echo "Services à traiter: ${env.CHANGES}"
                }
            }
        }

        // 3) Compilation des projets Angular et Maven
        stage('Project compilation') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            // Pour le front Angular : installation des dépendances et build
                            if (service == 'angular-front-end') {
                                powershell '''
                                    npm install
                                    npm run build
                                '''
                            } else {
                                // Pour les microservices Java : build Maven sans exécuter les tests
                                powershell 'mvn -B clean package -DskipTests'
                            }
                        }
                    }
                }
            }
        }

        // 4) Construction des images Docker pour les services à déployer
        stage('Build images') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    // Construit une image par service avec un tag basé sur le numéro de build Jenkins
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            powershell "docker build -t ${imageTag} ."
                        }
                    }
                }
            }
        }

        // 5) Authentification Docker (requise pour Docker Scout et push éventuels)
        stage('Docker Authentification') {
            steps {
                script {
                    // Se connecte à Docker Hub avec le token stocké de manière sécurisée
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

        // 6) Analyse de sécurité des images avec Docker Scout
        stage('Docker Scout') {
            steps {
                script {
                    // S'assure que le dossier de rapports existe
                    powershell '''
                        if (!(Test-Path "scout-report")) {
                            New-Item -ItemType Directory -Force -Path "scout-report"
                        }
                    '''

                    // Pour chaque image construite, génère un rapport (quickview + CVEs critiques)
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aperçu rapide et général des vulnérabilités
                            docker scout quickview ${imageTag} > scout-report/${service}_${env.BUILD_NUMBER}.txt

                            # Analyse détaillée des CVEs et ajout dans un rapport
                            docker scout cves ${imageTag} --exit-code --only-severity critical >> scout-report/${service}_${env.BUILD_NUMBER}.txt

                            # Nettoyage des fichiers temporaires après chaque analyse
                            if (Test-Path "$env:DOCKER_SCOUT_TEMP_DIR") {
                                Remove-Item -Path $env:DOCKER_SCOUT_TEMP_DIR -Recurse -Force -ErrorAction SilentlyContinue
                            }
                        """
                    }
                }
            }
        }

        // 7) Déploiement avec Docker Compose (complet ou sélectif)
        stage('Deploy with Docker Compose') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    // Met à jour les tags d'images dans le docker-compose pour pointer vers le build courant
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            powershell -Command "(Get-Content docker-compose.yml) -replace '${service}:latest', '${imageTag}' | Set-Content docker-compose.yml"
                        """
                    }

                    // Détermine si un déploiement complet est nécessaire (tous les services)

                    // Si aucun conteneur du stack n'est en cours d'exécution, effectuer un déploiement complet
                    def runningContainers = powershell(
                        script: 'docker compose ps -q',
                        returnStdout: true
                    ).trim()

                    if (!runningContainers) {
                        echo "Aucun conteneur en cours d'exécition pour le stack : déploiement complet"
                        powershell """
                            docker compose up -d
                        """
                    } else if (servicesList.sort() == microservices.sort()) {
                        echo "Déploiement complet de tous les services"
                        powershell """
                            docker compose down
                            docker compose up -d
                        """
                    } else {
                        // Déploiement sélectif : redémarre uniquement les services affectés
                        echo "Déploiement sélectif des services modifiés : ${servicesList}"
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
        // Gestion des échecs : messages de diagnostics orientés (sécurité, authentification, autres erreurs)
        failure {
            script {
                echo """/!\\ ECHEC DU PIPELINE /!\\
                Pipeline : ${env.JOB_NAME}
                Build : #${env.BUILD_NUMBER}
                Statut : ECHEC

                Consultez les logs du build (onglet Console Output) pour le détail.
                Si l'échec survient lors de l'étape d'authentification Docker, vérifiez:
                  - Le crédential 'DOCKER_PAT' dans Jenkins
                  - La validité du token Docker Hub
                  - Les permissions de l'utilisateur '${env.DOCKER_HUB_USER}'
                  - Que Docker Desktop est démarré et accessible
                """
            }
        }

        // Toujours exécuter un nettoyage de sécurité
        always {
            script {
                // Nettoyage final du répertoire temporaire Docker Scout (meilleure hygiène des builds)
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

        // Notification de réussite simple
        success {
            echo "Pipeline exécuté avec succès"
        }
    }
}