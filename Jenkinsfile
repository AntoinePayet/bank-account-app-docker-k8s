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

    }

    stages {

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

                    // Si aucun service n'a été modifié, on traite tous les services (déploiement complet)
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
                    if (servicesList.sort() == microservices.sort()) {
                        echo "Déploiement complet de tous les services"
                        powershell '''
                            docker compose down
                            docker compose up -d
                        '''
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
                def buildUrl = env.BUILD_URL
                def jobName = env.JOB_NAME
                def buildNumber = env.BUILD_NUMBER
                // Analyse les 2000 dernières lignes pour déterminer la cause probable
                def buildLog = currentBuild.rawBuild.getLog(2000).join('\n')

                if (buildLog.contains('docker scout cves')) {
                    // Échec dû à des vulnérabilités critiques détectées par Docker Scout
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
                    // Échec lié à l'authentification Docker Hub
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
                    // Échec générique (voir les logs détaillés de Jenkins pour investiguer)
                    echo """Pipeline : ${jobName}
                    Build : #${buildNumber}
                    Status : ÉCHEC
                    Raison : Erreur technique dans le pipeline
                    URL du build : ${buildUrl}
                    """
                }
            }
        }

        // Notification de réussite simple
        success {
            echo "Pipeline exécuté avec succès"
        }
    }
}