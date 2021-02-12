def call(Map config) {
  library("tdr-jenkinslib")
    
  def versionBumpBranch = "version-bump-${config.buildNumber}"
    
  pipeline {
    agent {
      label "master"
    }
    stages {
      stage("Run git secrets") {
        agent {
          label "master"
        }
        steps {
          script {
            tdr.runGitSecrets(config.libraryName)
          }
        }
      }
      stage("Deploy to s3") {
        agent {
          ecs {
            inheritFrom "base"
            taskDefinitionOverride "arn:aws:ecs:eu-west-2:${env.MANAGEMENT_ACCOUNT}:task-definition/s3publish"
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
                withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_TOKEN')]) {
                  sh "sbt +'release with-defaults'"
                }
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
