library("tdr-jenkinslib")

def repo = "tdr-jenkinslib"

pipeline {
  agent {
    label "master"
  }

  stages {
    stage("Run git-secrets") {
      steps {
        script {
          tdr.runGitSecrets(repo)
        }
      }
    }
  }
  post {
    failure {
      script {
        tdr.reportFailedBuiltToGitHub(repo, env.GIT_COMMIT)
      }
    }
    success {
      script {
        tdr.reportSuccessfulBuildToGitHub(repo, env.GIT_COMMIT)
      }
    }
  }
}
