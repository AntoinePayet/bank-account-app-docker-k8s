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
        stage('Clone Repository') {
            steps {
                git 'https://github.com/AntoinePayet/bank-account-app-docker-k8s.git'
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []
                    for (service in microservices) {
                        def changes = bat(
                            script: "git diff --name-only HEAD^..HEAD ${service}/",
                            returnStdout: true
                        ).trim()

                        if (changes) {
                            changedServices.add(service)
                        }
                    }

                    if (changedServices.isEmpty()) {
                        changedServices = microservices // Si aucun changement spécifique, construire tous les services
                    }

                    // Stocker la liste comme une chaîne séparée par des virgules dans env.CHANGES
                    env.CHANGES = changedServices.join(',')
                }
            }
        }

        stage('Build Projects') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            bat 'mvn -B clean package -DskipTests'
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            bat "docker -H tcp://localhost:2375 build -t ${imageTag} ."
                        }
                    }
                }
            }
        }

        stage('Deploy Services') {
            steps {
                script {
                    def servicesList = env.CHANGES.split(',')
                    for (service in servicesList) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        def containerName = service
                        def port = getServicePort(service)

                        bat """
                            docker -H tcp://localhost:2375 stop ${containerName} || true
                            docker -H tcp://localhost:2375 rm ${containerName} || true
                            docker -H tcp://localhost:2375 run --name ${containerName} -d -p ${port}:${port} ${imageTag}
                        """
                    }
                }
            }
        }
    }
}
