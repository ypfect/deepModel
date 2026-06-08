pipeline {
  agent {
    docker {
      image 'maven:3.9-eclipse-temurin-21'
      args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-m2:/root/.m2 --group-add 991'
    }
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build') {
      steps {
        sh 'mvn -B clean package -DskipTests'
      }
    }

    stage('Docker') {
      steps {
        sh '''
          if ! command -v docker >/dev/null 2>&1; then
            curl -fsSL https://download.docker.com/linux/static/stable/x86_64/docker-24.0.9.tgz -o /tmp/docker.tgz
            tar xzf /tmp/docker.tgz -C /tmp
            export PATH="/tmp/docker:$PATH"
          fi
          docker build -t deepmodel:${BUILD_NUMBER} .
        '''
      }
    }
  }

  post {
    success {
      archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
    }
  }
}
