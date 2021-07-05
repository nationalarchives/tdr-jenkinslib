def call(Map config) {
  library("tdr-jenkinslib")
  
  def terraformNode = config.containsKey("terraformNode") ? config.terraformNode : "terraform"
  
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
            inheritFrom "${terraformNode}"
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
        script {
          tdr.reportFailedBuildToGitHub(config.repo, env.GIT_COMMIT)
        }
      }
      success {
        script {
          tdr.reportSuccessfulBuildToGitHub(config.repo, env.GIT_COMMIT)
        }
      }
    }
  }
}
