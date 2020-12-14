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
            def image = "${env.MANAGEMENT_ACCOUNT}.dkr.ecr.eu-west-2.amazonaws.com/${config.imageName}"
            sh "aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin ${env.MANAGEMENT_ACCOUNT}.dkr.ecr.eu-west-2.amazonaws.com"
            sh "docker pull ${image}:${config.toDeploy}"
            sh "docker tag ${image}:${config.toDeploy} ${image}:${config.stage}"
            sh "docker push ${image}:${config.stage}"

            tdr.postToDaTdrSlackChannel(colour: "good", message: "*${config.imageName}* :whale: The '${config.toDeploy}' image has been tagged with '${config.stage}' in ECR")
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
            tdr.postToDaTdrSlackChannel(colour: "good", message: "*${config.ecsService}* :arrow_up: The app has been updated in ECS in the *${config.stage}* environment")
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
      failure {
        script {
          tdr.postToDaTdrSlackChannel(colour: "danger",
            message: "*${config.ecsService}* :warning: Deployment of *${config.toDeploy}* to the *${config.stage}* environment has failed"
          )
        }
      }
      unstable {
        script {
          tdr.postToDaTdrSlackChannel(colour: "warning",
            message: "*${config.ecsService}* :warning: Deployment of *${config.toDeploy}* to the *${config.stage}* environment was marked as 'unstable'"
          )
        }
      }
    }
  }
}
