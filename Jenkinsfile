pipeline {
  agent {
    docker {
      image 'maven:3.9-eclipse-temurin-21'
      args '-v /var/run/docker.sock:/var/run/docker.sock -v maven-m2:/root/.m2'
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
          docker run --rm \
            -v /var/run/docker.sock:/var/run/docker.sock \
            -v "$PWD:/w" -w /w \
            docker:24-cli \
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
