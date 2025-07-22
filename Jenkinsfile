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

    environment {
        DOCKER_REGISTRY = 'localhost:5000'
    }

    stages {
        stage('Checking Docker environment') {
            steps {
                script {
                    env.DOCKER_ENV_CONFIGURED = "false"

                    // V�rifier si Minikube est en cours d'ex�cution
                    def minikubeStatus = powershell(
                        script: '''
                            $status = minikube status
                            if ($LASTEXITCODE -ne 0) {
                                Write-Host "D�marrage de Minikube..."
                                minikube start --driver=hyperv

                                # Attendre que Minikube soit compl�tement d�marr�
                                $maxAttempts = 30
                                $attempt = 0
                                $ready = $false

                                Write-Host "Attente du d�marrage complet de Minikube..."
                                do {
                                    $attempt++
                                    Write-Host "Tentative $attempt sur $maxAttempts..."

                                    $status = minikube status
                                    if ($LASTEXITCODE -eq 0) {
                                        # V�rifier que tous les composants sont "Running"
                                        $statusOutput = minikube status --output=json | ConvertFrom-Json
                                        if ($statusOutput.Host -eq "Running" -and
                                            $statusOutput.Kubelet -eq "Running" -and
                                            $statusOutput.APIServer -eq "Running") {
                                            $ready = $true
                                            Write-Host "Minikube est compl�tement d�marr�!"
                                            break
                                        }
                                    }

                                    Start-Sleep -Seconds 10
                                } while ($attempt -lt $maxAttempts -and -not $ready)

                                if (-not $ready) {
                                    Write-Error "Timeout en attendant le d�marrage de Minikube"
                                    exit 1
                                }
                            } else {
                                Write-Host "Minikube est d�j� en cours d'ex�cution"
                            }
                            # V�rification finale du statut
                            minikube status
                        ''',
                        returnStatus: true
                    )

                    if (minikubeStatus != 0) {
                        error "Erreur lors du d�marrage/v�rification de Minikube"
                    }

                    // Configurer l'environnement Docker pour Minikube
                    def dockerEnvSetup = powershell(
                        script: '''
                            Write-Host "Configuration de l'environnement Docker..."
                            minikube -p minikube docker-env --shell powershell | Invoke-Expression
                            if ($?) {
                                Write-Output "true"
                            } else {
                                Write-Output "false"
                            }
                        ''',
                        returnStdout: true
                    ).trim()

                    echo "R�sultat de la configuration Docker: ${dockerEnvSetup}"

                    if (dockerEnvSetup == "true") {
                        env.DOCKER_ENV_CONFIGURED = true
                        powershell 'Write-Host "Docker Env Configured: $env:DOCKER_ENV_CONFIGURED"'
                        echo "Configuration Docker r�ussie"
                    } else {
                        error "�chec de la configuration de l'environnement Docker"
                    }

                    // V�rifier les variables d'environnement
                    powershell '''
                        Write-Host "=== V�rification de l'environnement Docker ==="
                        Write-Host "Docker Host: $env:DOCKER_HOST"
                        Write-Host "Docker Cert Path: $env:DOCKER_CERT_PATH"
                        Write-Host "Docker TLS Verify: $env:DOCKER_TLS_VERIFY"
                        Write-Host "Minikube Active Dockerd: $env:MINIKUBE_ACTIVE_DOCKERD"
                        Write-Host "Docker Registry: $env:DOCKER_REGISTRY"
                        Write-Host "Docker Env Configured: $env:DOCKER_ENV_CONFIGURED"
                        Write-Host "========================================"
                    '''
                }
            }
        }

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
                        // Si aucun changement sp�cifique, construire tous les services
                        changedServices = microservices
                    }

                    // Stocker la liste comme une cha�ne s�par�e par des virgules dans env.CHANGES
                    env.CHANGES = changedServices.join(',')
                }
            }
        }

//         stage('Build Projects') {
//             steps {
//                 script {
//                     def servicesList = env.CHANGES.split(',')
//                     for (service in servicesList) {
//                         dir(service) {
//                             powershell 'mvn -B clean package -DskipTests'
//                         }
//                     }
//                 }
//             }
//         }
//
//         stage('Build & Push Images Docker') {
//             when {
//                 expression {
//                     echo "V�rification de DOCKER_ENV_CONFIGURED: ${env.DOCKER_ENV_CONFIGURED}"
//                     return env.DOCKER_ENV_CONFIGURED == 'true'
//                 }
//             }
//             steps {
//                 script {
//                     echo "D�but de la construction des images Docker"
//                     def servicesList = env.CHANGES.split(',')
//                     for (service in servicesList) {
//                         dir(service) {
//                             def imageTag = "${DOCKER_REGISTRY}/${service}:${env.BUILD_NUMBER}"
//                             echo "Construction de l'image: ${imageTag}"
//                             powershell "docker build -t ${imageTag} ."
//                             echo "Push de l'image: ${imageTag}"
//                             powershell "docker push ${imageTag}"
//                         }
//                     }
//                 }
//             }
//         }

        stage('Minikube Deployment') {
            when {
                expression {
                    echo "V�rification de DOCKER_ENV_CONFIGURED pour d�ployer les microservices sur Minikube: ${env.DOCKER_ENV_CONFIGURED}"
                    return env.DOCKER_ENV_CONFIGURED == 'true'
                }
            }
            steps {
                script {
                    echo "Minikube est il lanc� / est il trouv� ?"
                    powershell "minikube status"
                    echo "D�but du d�ploiement sur Minikube"
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            echo "D�ploiement de ${service}"
                            powershell "minikube kubectl -- apply -f .\\${service}.yaml --validate=false"
                        }
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                if (env.DOCKER_ENV_CONFIGURED == 'false') {
                    echo "Le pipeline a �chou� en raison d'une mauvaise configuration de l'environnement Docker"
                }
            }
        }
    }
}
