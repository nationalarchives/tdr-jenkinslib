def call(Map config) {
  library("tdr-jenkinslib")
    
  def versionBumpBranch = "version-bump-${config.buildNumber}-${config.version}"
    
  pipeline {
    agent {
      label "master"
    }
    //Parameters section required for display in client Jenkins jobs GUI.
    //Selected values are not accessible within the function and must be passed into the function via the config map
    parameters {
      choice(name: "STAGE", choices: ["intg", "staging", "prod"], description: "The stage you are deploying the ${config.libraryName} library to")
    }
    stages {
      stage("Deploy to sonatype and commit changes to GitHub") {
        agent {
          ecs {
            inheritFrom "base"
            taskDefinitionOverride "arn:aws:ecs:eu-west-2:${env.MANAGEMENT_ACCOUNT}:task-definition/s3publish-${config.stage}:2"
          }
        }
        steps {
          script {
            tdr.configureJenkinsGitUser()
          }

          sh "git checkout ${versionBumpBranch}"

          sshagent(['github-jenkins']) {
            sh "sbt +'release with-defaults'"
          }

          slackSend color: "good", message: "*${config.libraryName}* :arrow_up: The ${config.libraryName} package has been published", channel: "#tdr-releases"

          script {
            tdr.pushGitHubBranch(versionBumpBranch)
          }
        }
      }
      stage("Create version bump pull request") {
        agent {
          label "master"
        }
        steps {
          script {
            tdr.createGitHubPullRequest(
              pullRequestTitle: "Version Bump from build number ${config.buildNumber}",
              buildUrl: env.BUILD_URL,
              repo: "${config.repo}",
              branchToMergeTo: "master",
              branchToMerge: versionBumpBranch
            )
          }
        }
      }
    }
  }
}
