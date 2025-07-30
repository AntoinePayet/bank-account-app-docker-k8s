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
                    // powershell 'docker scout version'

                    // Connexion à Docker Hub avec le PAT
                    withCredentials([string(credentialsId: 'DOCKER_PAT', variable: 'DOCKER_PAT')]) {
                        try {
                            // Connexion via un fichier temporaire sécurisé
                            powershell '''
                                # Créer un fichier temporaire sécurisé
                                $tempFile = New-TemporaryFile
                                $env:DOCKER_PAT | Out-File -FilePath $tempFile.FullName

                                # Utiliser le fichier pour la connexion
                                Get-Content $tempFile.FullName | docker login --username antoinepayet --password-stdin

                                # Nettoyer immédiatement le fichier
                                Remove-Item -Path $tempFile.FullName -Force

                                # Vérifier le statut
                                if ($LASTEXITCODE -ne 0) {
                                    throw "Échec de la connexion Docker"
                                }
                            '''

                            def servicesList = env.CHANGES.split(',')

                            for (service in servicesList) {
                                def imageTag = "${service}:${env.BUILD_NUMBER}"

                                // Vérifier que l'image existe
                                powershell """
                                    if (-not (docker images -q ${imageTag})) {
                                        Write-Error "L'image ${imageTag} n'existe pas"
                                        exit 1
                                    }
                                """

                                // Analyse avec Docker Scout
                                powershell """
                                    docker -H tcp://localhost:2375 scout quickview ${imageTag} || Write-Warning "Analyse quickview échouée pour ${imageTag}"
                                    docker -H tcp://localhost:2375 scout cves ${imageTag} --only-severity critical,high || Write-Warning "Analyse CVE échouée pour ${imageTag}"
                                    docker -H tcp://localhost:2375 scout report ${imageTag} > scout-report-${service}.txt || Write-Warning "Génération du rapport échouée pour ${imageTag}"
                                """
                            }
                        } catch (Exception e) {
                            powershell 'docker logout'
                            error "Erreur dans l'étape Docker Scout: ${e.message}"
                        } finally {
                            powershell 'docker logout'
                        }
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