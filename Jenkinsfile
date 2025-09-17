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

    // Variables d'environnement globales accessibles dans tout le pipeline
    environment {
        // Jeton d'acc�s Docker Hub stock� dans Jenkins Cr�dentials
        DOCKER_HUB_PAT = credentials('DOCKER_PAT')
        // Nom d'utilisateur Docker Hub (non sensible)
        DOCKER_HUB_USER = 'antoinepayet'
        // H�te Docker (Docker Desktop expos� via le daemon TCP)
//         DOCKER_HOST = 'tcp://localhost:2375'

    }

    stages {

        // 2) D�tection des changements pour construire/d�ployer uniquement les services modifi�s
        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []
                    for (service in microservices) {
                        // Compare les fichiers modifi�s entre le dernier commit et l'actuel pour chaque service
                        def changes = powershell(
                            script: "git diff --name-only HEAD^..HEAD ${service}/",
                            returnStdout: true
                        ).trim()

                        // Si des changements sont d�tect�s dans le dossier du service, on l'ajoute � la liste
                        if (changes) {
                            changedServices.add(service)
                        }
                    }

                    // Si aucun service n'a �t� modifi�, on traite tous les services (d�ploiement complet)
                    if (changedServices.isEmpty()) {
                        changedServices = microservices
                        echo "Aucun changement d�tect� : tous les services seront d�ploy�s"
                    }

                    // Partage la liste des services � traiter avec les stages suivants
                    env.CHANGES = changedServices.join(',')
                    echo "Services � traiter: ${env.CHANGES}"
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
                            // Pour le front Angular : installation des d�pendances et build
                            if (service == 'angular-front-end') {
                                powershell '''
                                    npm install
                                    npm run build
                                '''
                            } else {
                                // Pour les microservices Java : build Maven sans ex�cuter les tests
                                powershell 'mvn -B clean package -DskipTests'
                            }
                        }
                    }
                }
            }
        }

        // 4) Construction des images Docker pour les services � d�ployer
        stage('Build images') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    // Construit une image par service avec un tag bas� sur le num�ro de build Jenkins
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            powershell "docker build -t ${imageTag} ."
                        }
                    }
                }
            }
        }

        // 5) Authentification Docker (requise pour Docker Scout et push �ventuels)
        stage('Docker Authentification') {
            steps {
                script {
                    // Se connecte � Docker Hub avec le token stock� de mani�re s�curis�e
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

        // 6) Analyse de s�curit� des images avec Docker Scout
        stage('Docker Scout') {
            steps {
                script {
                    // S'assure que le dossier de rapports existe
                    powershell '''
                        if (!(Test-Path "scout-report")) {
                            New-Item -ItemType Directory -Force -Path "scout-report"
                        }
                    '''

                    // Pour chaque image construite, g�n�re un rapport (quickview + CVEs critiques)
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            # Aper�u rapide et g�n�ral des vuln�rabilit�s
                            docker scout quickview ${imageTag} > scout-report/${service}_${env.BUILD_NUMBER}.txt

                            # Analyse d�taill�e des CVEs et ajout dans un rapport
                            docker scout cves ${imageTag} --exit-code --only-severity critical >> scout-report/${service}_${env.BUILD_NUMBER}.txt
                        """
                    }
                }
            }
        }

        // 7) D�ploiement avec Docker Compose (complet ou s�lectif)
        stage('Deploy with Docker Compose') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    // Met � jour les tags d'images dans le docker-compose pour pointer vers le build courant
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        powershell """
                            powershell -Command "(Get-Content docker-compose.yml) -replace '${service}:latest', '${imageTag}' | Set-Content docker-compose.yml"
                        """
                    }

                    // D�termine si un d�ploiement complet est n�cessaire (tous les services)
                    if (servicesList.sort() == microservices.sort()) {
                        echo "D�ploiement complet de tous les services"
                        powershell '''
                            docker compose down
                            docker compose up -d
                        '''
                    } else {
                        // D�ploiement s�lectif : red�marre uniquement les services affect�s
                        echo "D�ploiement s�lectif des services modifi�s : ${servicesList}"
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
        // Gestion des �checs : messages de diagnostics orient�s (s�curit�, authentification, autres erreurs)
        failure {
            script {
                def buildUrl = env.BUILD_URL
                def jobName = env.JOB_NAME
                def buildNumber = env.BUILD_NUMBER
                // Analyse les 2000 derni�res lignes pour d�terminer la cause probable
                def buildLog = currentBuild.rawBuild.getLog(2000).join('\n')

                if (buildLog.contains('docker scout cves')) {
                    // �chec d� � des vuln�rabilit�s critiques d�tect�es par Docker Scout
                    echo """/!\\ ALERTE DE S�CURIT� /!\\
                    Pipeline : ${jobName}
                    Build : #${buildNumber}
                    Status : �CHEC
                    Raison : Des vuln�rabilit�s critiques ont �t� d�tect�es
                    URL du build : ${buildUrl}

                    Veuillez consulter les rapports de s�curit� dans le dossier 'scout-report' pour plus de d�tails.
                    """
                } else if (buildLog.toLowerCase().contains('error logging in') ||
                           buildLog.contains('unauthorized') ||
                           buildLog.contains('authentification required') ||
                           buildLog.contains('access denied') ||
                           buildLog.contains('permission denied')) {
                    // �chec li� � l'authentification Docker Hub
                    echo """/!\\ ERREUR D'AUTHENTIFICATION DOCKER /!\\
                    Pipeline : ${jobName}
                    Build : #${buildNumber}
                    Status : �CHEC
                    Raison : Des vuln�rabilit�s critiques ont �t� d�tect�es
                    URL du build : ${buildUrl}

                    Actions recommand�es :
                    1. V�rifier que le credential 'DOCKER_PAT' est correctement configur� dans Jenkins
                    2. V�rifier que le token Docker Hub n'a pas expir�
                    3. V�rifier que l'utilisateur '${env.DOCKER_HUB_USER}' a les permissions nec�ssaires
                    4. V�rifier que Docker Desktop est bien d�marr� et accessible
                    """
                } else {
                    // �chec g�n�rique (voir les logs d�taill�s de Jenkins pour investiguer)
                    echo """Pipeline : ${jobName}
                    Build : #${buildNumber}
                    Status : �CHEC
                    Raison : Erreur technique dans le pipeline
                    URL du build : ${buildUrl}
                    """
                }
            }
        }

        // Notification de r�ussite simple
        success {
            echo "Pipeline ex�cut� avec succ�s"
        }
    }
}