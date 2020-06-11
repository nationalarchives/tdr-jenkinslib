def call(Map config) {
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
              sh "docker pull nationalarchives/${config.IMAGE_NAME}:${config.TO_DEPLOY}"
              sh "docker tag nationalarchives/${config.IMAGE_NAME}:${config.TO_DEPLOY} nationalarchives/${config.IMAGE_NAME}:${config.STAGE}"
              sh "docker push nationalarchives/${config.IMAGE_NAME}:${config.STAGE}"

              slackSend color: "good", message: "*${config.IMAGE_NAME}* :whale: The '${config.TO_DEPLOY}' image has been tagged with '${config.STAGE}' in Docker Hub", channel: "#tdr-releases"
            }
          }
        }
      }
      stage("Update ECS container") {
        agent {
          ecs {
            inheritFrom "aws"
            taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/TDRJenkinsNodeRole${config.STAGE.capitalize()}"
          }
        }
        steps {
          script {
            def accountNumber = tdr.getAccountNumberFromStage("${config.STAGE}")

            sh "python3 /update_service.py ${accountNumber} ${config.STAGE} ${config.ECS_SERVICE}"
            slackSend color: "good", message: "*${config.ECS_SERVICE}* :arrow_up: The app has been updated in ECS in the *${cofnig.STAGE}* environment", channel: "#tdr-releases"
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
          if (config.STAGE == "intg") {
            int delaySeconds = config.TEST_DELAY_SECONDS

            tdr.runEndToEndTests(delaySeconds, config.STAGE, BUILD_URL)
          }
        }
      }
    }
  }
}
