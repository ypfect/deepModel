pipeline {
  agent none

  stages {
    stage('Build') {
      agent {
        docker {
          image 'maven:3.9-eclipse-temurin-21'
          args '-v maven-m2:/root/.m2'
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
        sh 'docker build -t deepmodel:${BUILD_NUMBER} .'
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
