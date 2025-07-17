def microservices = [
    'account-service',
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

    stages {
        stage('Clone Repository') {
            steps {
                git 'https://github.com/AntoinePayet/bank-account-app-docker-k8s.git'
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    env.CHANGES = []
                    for (service in microservices) {
                        def changes = sh(
                            script: "git diff --name-only HEAD^..HEAD ${service}/",
                            returnStdout: true
                        ).trim()

                        if (changes) {
                            env.CHANGES.add(service)
                        }
                    }

                    if (env.CHANGES.isEmpty()) {
                        env.CHANGES = microservices // Si aucun changement spécifique, construire tous les services
                    }
                }
            }
        }

        stage('Build Projects') {
            steps {
                script {
                    for (service in env.CHANGES) {
                        dir(service) {
                            sh 'mvn -B clean package -DskipTests'
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    for (service in env.CHANGES) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            sh "docker -H tcp://localhost:2375 build -t ${imageTag} ."
                        }
                    }
                }
            }
        }

        stage('Deploy Services') {
            steps {
                script {
                    for (service in env.CHANGES) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        def containerName = service
                        def port = getServicePort(service)

                        sh """
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