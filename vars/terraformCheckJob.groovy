def call(Map config) {
  library("tdr-jenkinslib")
  pipeline {
    agent {
      label "master"
    }
    stages {
      stage("Run git secrets") {
        steps {
          script {
            tdr.runGitSecrets(config.repo)
          }
        }
      }
      stage('Check Terraform') {
        agent {
          ecs {
            inheritFrom 'terraform'
          }
        }
        environment {
          //no-color option set for Terraform commands as Jenkins console unable to output the colour
          //making output difficult to read
          TF_CLI_ARGS = "-no-color"
        }
        stages {
          stage('Check Terraform formatting') {
            steps {
              script {
                tdr.reportStartOfBuildToGitHub(config.repo, env.GIT_COMMIT)
              }
              checkout scm
              dir("${config.terraformDirectoryPath}") {
                sh 'terraform fmt -check -recursive'
              }
            }
          }
        }
      }
    }
    post {
      cleanup {
        deleteDir()
      }
      failure {
        node('master') {
          script {
            tdr.reportFailedBuildToGitHub(config.repo, env.GIT_COMMIT)
          }
        }
      }
      success {
        node('master') {
          script {
            tdr.reportSuccessfulBuildToGitHub(config.repo, env.GIT_COMMIT)
          }
        }
      }
    }
  }
}