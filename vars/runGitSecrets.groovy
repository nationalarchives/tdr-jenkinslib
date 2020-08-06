def call(String repo) {
  library("tdr-jenkinslib")
  pipeline {
    agent {
      label "master"
    }
    stages {
      stage("Deploy lambda") {
        steps {
          script {
            sh "set +e"
            def exitCode = sh(script: "git-secrets --scan", returnStatus: true)
            sh "set -e"
            if(exitCode == 1) {
              postToDaTdrSlackChannel([colour: "danger", message: "Secrets found in repository ${repo} ${BUILD_URL}"])
              sh "false"
            }
          }
        }
      }
    }
    post {
      success {
        script {
          tdr.runEndToEndTests(0, config.stage, BUILD_URL)
        }
      }
    }
  }
}