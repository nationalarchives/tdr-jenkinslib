def call(Map config) {
  pipeline {
    agent {
      label "master"
    }
    //Parameters section required for display in client Jenkins jobs GUI.
    //Selected values are not accessible within the function and must be passed into the function via the config map
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
              sh "docker pull nationalarchives/${config.imageName}:${config.toDeploy}"
              sh "docker tag nationalarchives/${config.imageName}:${config.toDeploy} nationalarchives/${config.imageName}:${config.stage}"
              sh "docker push nationalarchives/${config.imageName}:${config.stage}"

              slackSend color: "good", message: "*${config.imageName}* :whale: The '${config.toDeploy}' image has been tagged with '${config.stage}' in Docker Hub", channel: "#tdr-releases"
            }
          }
        }
      }
      stage("Update ECS container") {
        agent {
          ecs {
            inheritFrom "aws"
            taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/TDRJenkinsNodeRole${config.stage.capitalize()}"
          }
        }
        steps {
          script {
            def accountNumber = tdr.getAccountNumberFromStage("${config.stage}")

            sh "python3 /update_service.py ${accountNumber} ${config.stage} ${config.ecsService}"
            slackSend color: "good", message: "*${config.ecsService}* :arrow_up: The app has been updated in ECS in the *${config.stage}* environment", channel: "#tdr-releases"
          }
        }
      }
      stage("Update release branch") {
        agent {
          label "master"
        }
        steps {
          script {
            def releaseBranch = "release-${config.stage}"

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
          if (config.stage == "intg") {
            int delaySeconds = config.testDelaySeconds

            tdr.runEndToEndTests(delaySeconds, config.stage, BUILD_URL)
          }
        }
      }
    }
  }
}
