def call(Map config) {
  library("tdr-jenkinslib")
    
  def versionBumpBranch = "version-bump-${config.buildNumber}"
    
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
      stage("Run git secrets") {
        agent {
          label "master"
        }
        steps {
          script {
            tdr.runGitSecrets(repo)
          }
        }
      }
      stage("Deploy to s3") {
        agent {
          ecs {
            inheritFrom "base"
            taskDefinitionOverride "arn:aws:ecs:eu-west-2:${env.MANAGEMENT_ACCOUNT}:task-definition/s3publish-${config.stage}:2"
          }
        }
        stages {
          stage("Create and push version bump GitHub branch") {
            steps {
              script {
                tdr.configureJenkinsGitUser()
              }

              sh "git checkout -b ${versionBumpBranch}"

              //sbt release requires branch to be on origin first
              script {
                tdr.pushGitHubBranch(versionBumpBranch)
              }
            }
          }
          stage("Publish to s3") {
            steps {
              //Commits to origin branch
              sshagent(['github-jenkins']) {
                sh "sbt +'release with-defaults'"
              }
              script {
                tdr.postToDaTdrSlackChannel(colour: "good",
                                            message: "*${config.libraryName}* :arrow_up: The ${config.libraryName} package has been published"
                )
              }
            }
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
