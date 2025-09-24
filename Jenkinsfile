// Liste des microservices à gérer dans le pipeline
def microservices = [
    'account-service',
    'angular-front-end',
    'customer-service',
    'config-service',
    'discovery-service',
    'gateway-service'
]

// Liste des bases de données
def databases = [
    'db_postgres',
    'db_postgres_1'
]

// Liste des microservices à ne pas redéployer (qui doivent rester arrêtés)
def nePasDeployer = []

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
        // 0) Display lists of all kinds of services
        stage('Affichage des listes') {
            steps {
                script {
                    echo "Microservices: ${microservices}"
                    echo "Databases: ${databases}"
                    echo "Microservices à ne pas déployer: ${nePasDeployer}"
                }
            }
        }

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

        // 2) Détection des changements pour déployer les services modifiés ou pas encore déployés
        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []

                    // Premier run: pas de build précédent ET build #1 => déploiement complet
                    def isFirstRun = (currentBuild?.previousSuccessfulBuild == null && env.BUILD_NUMBER == '1')

                    if (isFirstRun) {
                        echo "Premier lancement du pipeline : déploiement complet de tous les services"
                        changedServices = microservices
                    } else {
                        for (service in microservices) {
                            // Compare les fichiers modifiés entre le dernier commit et l'actuel pour chaque microservice
                            def changes = powershell(
                                script: "git diff --name-only HEAD^..HEAD ${service}/ 2>&1",
                                returnStdout: true
                            ).trim()

                            // Si des changements sont détectés dans le dossier du microservice, on l'ajoute à la liste
                            if (changes) {
                                changedServices.add(service)
                            }
                        }

                        // Inclure les services qui n'ont jamais été déployés (aucun conteneur créé)
                        for (service in microservices) {
                            def hasAnyContainer = powershell (
                                script: "docker compose ps -a -q ${service} 2>&1",
                                returnStdout: true
                            ).trim()
                            if (!hasAnyContainer){
                                echo "Microservice '${service}' sans conteneur existant : inclusion pour déploiement initial"
                                changedServices.add(service)
                            }
                        }
                    }

                    // Déduplication + déploiement de tous les services si aucune modification détectée
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

        // 3) Compilation des projets (Node pour Angular, Maven pour les microservices Java)
        stage('Project compilation') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            // Pour le front Angular : installation des dépendances et build
                            if (service == 'angular-front-end') {
                                powershell '''
                                    npm ci 2>&1
                                    npm run build 2>&1
                                '''
                            } else {
                                // Pour les microservices Java : build Maven sans exécuter les tests
                                powershell 'mvn -B clean package -DskipTests 2>&1'
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
                    // Construit une image par microservice avec un tag basé sur le numéro de build Jenkins
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            powershell "docker build -t ${imageTag} ."
                        }
                    }
                }
            }
        }

        // 5) Authentification Docker (requise pour Docker Scout)
        stage('Docker Authentification') {
            steps {
                script {
                    // Se connecte à Docker Hub avec le token stocké de manière sécurisée
                    withCredentials([string(credentialsId: 'DOCKER_PAT', variable: 'DOCKER_HUB_PAT')]) {
                        powershell '''
                            $password = $env:DOCKER_HUB_PAT
                            $username = $env:DOCKER_HUB_USER
                            $password | docker login -u $username --password-stdin
                            echo y | docker extension install docker/scout-extension 2>&1
                        '''
                    }
                }
            }
        }

        // 6) Analyse de sécurité des images avec Docker Scout
        stage('Docker Scout') {
            steps {
                script {
                    // Prépare le répertoire de rapport pour collecter les sorties
                    powershell '''
                        if (!(Test-Path "scout-report")) {
                            New-Item -ItemType Directory -Force -Path "scout-report" | Out-Null
                        }
                    '''

                    // Pour chaque image construite, génère un rapport (quickview + CVEs critiques)
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aperçu rapide et général des vulnérabilités
                            docker scout quickview ${imageTag} > scout-report/${service}_${env.BUILD_NUMBER}.txt 2>&1

                            # Analyse détaillée des CVEs et ajout dans un rapport
                            docker scout cves ${imageTag} --exit-code --only-severity critical >> scout-report/${service}_${env.BUILD_NUMBER}.txt 2>&1

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
                            powershell -Command "(Get-Content docker-compose.yml) -replace '${service}:latest', '${imageTag}' | Set-Content docker-compose.yml" 2>&1
                        """
                    }

                    // Pour les microservices non modifiés, conserve le tag actuellement utilisé par les conteneurs
                    def notChanged = microservices.findAll { !servicesList.contains(it) }
                    for (service in notChanged) {
                        def containerId = powershell (
                            script: "docker compose ps -q -a ${service} 2>&1",
                            returnStdout: true
                        ).trim()
                        if (containerId) {
                            def runningImage = powershell(
                                script: "docker inspect --format='{{.Config.Image}}' ${containerId} 2>&1",
                                returnStdout: true
                            ).trim()
                            if (runningImage && runningImage.contains(':')) {
                                def tag = runningImage.substring(runningImage.lastIndexOf(':') + 1)
                                powershell """
                                    powershell -Command "(Get-Content docker-compose.yml) -replace '${service}:latest', '${service}:${tag}' | Set-Content docker-compose.yml" 2>&1
                                """
                            }
                        }
                    }

                    def allMicroservices = microservices.join(' ')
                    // Liste des microservices en cours d'exécution
                    def runningList = powershell(
                        script: "docker compose ps --format '{{.Service}}' --status=running ${allMicroservices} 2>&1",
                        returnStdout: true
                    ).trim()
                    def runningServices = runningList ? runningList.split().toList() : []

                    // Liste des microservices non-running (exited/created/dead)
                    def notRunningList = powershell(
                        script: "docker compose ps --format '{{.Service}}' -a --status=exited --status=created --status=dead ${allMicroservices} 2>&1",
                        returnStdout: true
                    ).trim()
                    def notRunningServices = notRunningList ? notRunningList.split().toList() : []

                    // Liste les databases non-running (exited/created/dead)
                    def allDatabase = databases.join(' ')
                    def notRunningDatabases = powershell (
                        script: "docker compose ps --format '{{.Service}}' -a --status=exited --status=created --status=dead --status=unknown ${allDatabase} 2>&1",
                        returnStdout: true
                    )
                    def notRunningDb = notRunningDatabases ? notRunningDatabases.split().toList() : []


                    // Aucun conteneur de databases n'est arrêtés
                    if (notRunningDb.isEmpty()) {
                        if (runningServices.isEmpty()) {
                            // Aucun Conteneur en cours => déploiement complet
                            echo "Aucun conteneur en cours d'exécution pour le stack : déploiement complet"
                            powershell "docker compose up -d ${allMicroservices}"

                        } else if (servicesList.sort() == microservices.sort()) {
                            // Tous les microservices sont concernés => redéploiement complet
                            echo "Déploiement de tous les microservices"
                            powershell """
                                docker compose stop ${allMicroservices}
                                docker compose up -d ${allMicroservices}
                            """

                        } else if (!notRunningServices.isEmpty()) {
                            // Déployer les microservices modifiés + ceux arrêtés
                            echo "Déploiement des microservices modifiés ainsi que les conteneurs arrêtés"
                            def toStart = []
                            def seen = new HashSet()
                            for (s in servicesList) {
                                if (s && seen.add(s)) toStart.add(s)
                            }
                            for (s in notRunningServices) {
                                if (s && seen.add(s)) toStart.add(s)
                            }
                            echo "Services à (re)démarrer: ${toStart}"
                            def svc = toStart.join(' ')
                            powershell """
                                docker compose stop ${svc}
                                docker compose up -d ${svc}
                            """

                        } else {
                            // Déploiement sélectif limité aux microservices modifiés
                            echo "Déploiement sélectif des microservices modifiés : ${servicesList}"
                            def svc = servicesList.join(' ')
                            powershell """
                                docker compose stop ${svc}
                                docker compose up -d ${svc}
                            """
                        }

                    // Un ou plusieurs conteneur(s) de databases est arrêtés
                    } else {
                        def toDeploy = (microservices + notRunningDb).unique().join(' ')
                        if (runningServices.isEmpty()) {
                            // Aucun Conteneur de microservices en cours => déploiement complet
                            echo """
                                Aucun conteneur en cours d'exécution pour le stack : déploiement complet
                                Databases à (re)démarrer: ${notRunningDb}
                            """
                            powershell "docker compose up -d ${toDeploy}"

                        } else if (servicesList.sort() == microservices.sort()) {
                            // Tous les services sont concernés => redéploiement complet
                            echo """
                                Déploiement de tous les microservices
                                Databases à (re)démarrer: ${notRunningDb}
                            """
                            powershell """
                                docker compose stop ${toDeploy}
                                docker compose up -d ${toDeploy}
                            """

                        } else if (!notRunningServices.isEmpty()) {
                            // Déployer les services modifiés + ceux arrêtés
                            echo """
                                Déploiement des microservices modifiés ainsi que les conteneurs arrêtés
                                Databases à (re)démarrer: ${notRunningDb}
                            """
                            def toStart = []
                            def seen = new HashSet()
                            for (s in servicesList) {
                                if (s && seen.add(s)) toStart.add(s)
                            }
                            for (s in notRunningServices) {
                                if (s && seen.add(s)) toStart.add(s)
                            }
                            echo """
                                Microservices à (re)démarrer: ${toStart}
                                Databases à (re)démarrer: ${notRunningDb}
                            """
                            def svc = (toStart + notRunningDb)unique().join(' ')
                            powershell """
                                docker compose stop ${svc}
                                docker compose up -d ${svc}
                            """

                        } else {
                            // Déploiement sélectif limité aux services modifiés
                            echo """
                                Déploiement sélectif des microservices modifiés : ${servicesList}
                                Databases à (re)démarrer: ${notRunningDb}
                            """
                            def svc = (servicesList + notRunningDb).join(' ')
                            powershell """
                                docker compose stop ${svc} 2>&1
                                docker compose up -d ${svc} 2>&1
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
                      - Que le Daemon Docker est démarré et accessible
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
                            Write-Warning "Avertissement: Impossible de nettoyer complètement $env:DOCKER_SCOUT_TEMP_DIR : $($_.Exception.Message)"
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
