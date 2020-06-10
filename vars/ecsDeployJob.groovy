def call(Map params) { 
  pipeline {
    agent {
      label "master"
    }
      parameters {
        choice(name: "STAGE", choices: ["intg", "staging", "prod"], description: "The stage you are deploying to")
        string(name: "TO_DEPLOY", description: "The git tag, branch or commit reference to deploy, e.g. 'v123'")
      }
      stages {
        stage("Docker") {
          agent {
            label "master"
          }
          steps {
            script {
              docker.withRegistry('', 'docker') {
                sh "docker pull nationalarchives/${params.IMAGE_NAME}:${params.TO_DEPLOY}"
                sh "docker tag nationalarchives/${params.IMAGE_NAME}:${params.TO_DEPLOY} nationalarchives/${params.IMAGE_NAME}:${params.STAGE}"
                sh "docker push nationalarchives/${params.IMAGE_NAME}:${params.STAGE}"

                slackSend color: "good", message: "*${params.IMAGE_NAME}* :whale: The '${params.TO_DEPLOY}' image has been tagged with '${params.STAGE}' in Docker Hub", channel: "#tdr-releases"
              }
            }
          }
        }
        stage("Update ECS container") {
          agent {
            ecs {
              inheritFrom "aws"
              taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/TDRJenkinsNodeRole${params.STAGE.capitalize()}"
            }
          }
          steps {
            script {
              def accountNumber = tdr.getAccountNumberFromStage("${params.STAGE}")

              sh "python3 /update_service.py ${accountNumber} ${params.STAGE} ${params.ECS_SERVICE}"
              slackSend color: "good", message: "*${params.ECS_SERVICE}* :arrow_up: The app has been updated in ECS in the *${params.STAGE}* environment", channel: "#tdr-releases"
            }
          }
        }
        stage("Update release branch") {
          agent {
            label "master"
          }
          steps {
            script {
              def releaseBranch = "release-${env.STAGE}"

              sh "git branch -f ${releaseBranch} HEAD"
              sshagent(['github-jenkins']) {
                sh("git push -f origin ${releaseBranch}")
              }
            }
          }
        }
      }
      post {
        success {
          script {
            if (params.STAGE == "intg") {
              int delaySeconds = params.TEST_DELAY_SECONDS
                        
              tdr.runEndToEndTests(delaySeconds, params.STAGE, BUILD_URL)
            }
          }
        }
      }
    }
}
