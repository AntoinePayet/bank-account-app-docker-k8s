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

    environment {
        DOCKER_HOME = tool name: 'MyDocker'
        PATH = "${DOCKER_HOME}:${env.PATH}"
    }

    stages {
        stage('V�rification Docker') {
            steps {
                script {
                    try {
                        sh 'docker --version'
                    } catch (Exception e) {
                        error "Docker n'est pas correctement install� ou configur�: ${e.message}"
                    }
                }
            }
        }

        stage('Clone Repository') {
            steps {
                checkout scm
            }
        }

        stage('Detect Changes') {
            steps {
                script {
                    def changedServices = []
                    for (service in microservices) {
                        def changes = sh(
                            script: "git diff --name-only HEAD^..HEAD ${service}/ || true",
                            returnStdout: true
                        ).trim()

                        if (changes) {
                            changedServices.add(service)
                        }
                    }

                    env.CHANGED_SERVICES = changedServices.isEmpty() ? microservices.join(',') : changedServices.join(',')
                    echo "Services � construire: ${env.CHANGED_SERVICES}"
                }
            }
        }

        stage('Build Projects') {
            steps {
                script {
                    def servicesToBuild = env.CHANGED_SERVICES.split(',')
                    for (service in servicesToBuild) {
                        dir(service) {
                            bat "mvn -B clean package -DskipTests"
                        }
                    }
                }
            }
        }

        stage('Build Docker Images') {
            steps {
                script {
                    def servicesToBuild = env.CHANGED_SERVICES.split(',')
                    for (service in servicesToBuild) {
                        dir(service) {
                            def imageTag = "${service}:${env.BUILD_NUMBER}"
                            bat "docker build -t ${imageTag} ."
                        }
                    }
                }
            }
        }

        stage('Deploy Services') {
            steps {
                script {
                    def servicesToDeploy = env.CHANGED_SERVICES.split(',')
                    for (service in servicesToDeploy) {
                        def imageTag = "${service}:${env.BUILD_NUMBER}"
                        def containerName = service
                        def port = getServicePort(service)

                        bat """
                            docker stop ${containerName} 2>nul || echo "Container not running"
                            docker rm ${containerName} 2>nul || echo "Container not found"
                            docker run --name ${containerName} -d -p ${port}:${port} ${imageTag}
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo 'Pipeline ex�cut� avec succ�s!'
        }
        failure {
            echo 'Le pipeline a �chou�. V�rifiez les logs pour plus de d�tails.'
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