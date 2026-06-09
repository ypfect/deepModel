def REGISTRY = 'localhost:5050'
def IMAGE_NAME = 'deepmodel'

pipeline {
  agent none

  stages {
    stage('Log') {
      agent { label 'built-in' }
      steps {
        echo "=== deepModel CI/CD ==="
        echo "Job: ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        echo "Branch: ${env.BRANCH_NAME ?: 'main'}"
        echo "Source: https://github.com/ypfect/deepModel.git"
        echo "Registry: ${REGISTRY}/${IMAGE_NAME}"
        echo "Build URL: ${env.BUILD_URL}"
      }
    }

    stage('Build') {
      agent {
        docker {
          image 'maven:3.9-eclipse-temurin-21'
          args '-v maven-m2:/root/.m2 ' +
               '-e HTTP_PROXY=http://host.docker.internal:7890 ' +
               '-e HTTPS_PROXY=http://host.docker.internal:7890 ' +
               '-e NO_PROXY=localhost,127.0.0.1'
        }
      }
      stages {
        stage('Checkout') {
          steps {
            checkout scm
          }
        }
        stage('Package') {
          steps {
            sh 'mvn -B clean package -DskipTests'
            stash includes: 'target/**,Dockerfile,.dockerignore', name: 'artifacts'
          }
        }
      }
    }

    stage('Docker') {
      agent { label 'built-in' }
      steps {
        unstash 'artifacts'
        sh "docker build -t ${REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER} -t ${REGISTRY}/${IMAGE_NAME}:latest ."
      }
    }

    stage('Push') {
      agent { label 'built-in' }
      steps {
        sh "docker push ${REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}"
        sh "docker push ${REGISTRY}/${IMAGE_NAME}:latest"
      }
    }

    stage('Deploy') {
      agent { label 'built-in' }
      steps {
        build job: 'deepModel-deploy',
              parameters: [
                string(name: 'IMAGE', value: "${REGISTRY}/${IMAGE_NAME}:${BUILD_NUMBER}")
              ],
              wait: true
      }
    }
  }

  post {
    success {
      node('built-in') {
        unstash 'artifacts'
        archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
      }
    }
  }
}
