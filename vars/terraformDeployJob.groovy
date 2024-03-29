def call(Map config) {
  library("tdr-jenkinslib")

  def terraformWorkspace = config.stage == "mgmt" ? "default" : config.stage
  def terraformModulesBranch = config.containsKey("modulesBranch") ? config.modulesBranch : "master"
  def terraformNode = config.containsKey("terraformNode") ? config.terraformNode : "terraform"
  def versionTag = "v${env.BUILD_NUMBER}"

  pipeline {
    agent {
      label "built-in"
    }
    //Parameters section required for display in client Jenkins jobs GUI.
    //Selected values are not accessible within the function and must be passed into the function via the config map
    parameters {
      choice(name: "STAGE", choices: ["intg", "staging", "prod"], description: "The environment you are deploying Terraform changes to")
    }
    stages {
      stage("Run git secrets") {
        steps {
          script {
            tdr.runGitSecrets(config.repo)
          }
        }
      }
      stage('Run Terraform build') {
        agent {
          ecs {
            inheritFrom "${terraformNode}"
              taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/${config.taskRoleName}"
          }
        }
        environment {
          TF_VAR_tdr_account_number = tdr.getAccountNumberFromStage(config.stage)
          //no-color option set for Terraform commands as Jenkins console unable to output the colour
          //making output difficult to read
          TF_CLI_ARGS = "-no-color"
          GITHUB_OWNER = "nationalarchives"
        }
        stages {
          stage('Set up Terraform workspace') {
            steps {
              dir("${config.terraformDirectoryPath}") {
                echo 'Initializing Terraform...'
                sh "git clone --branch ${terraformModulesBranch} https://github.com/nationalarchives/tdr-terraform-modules.git"
                sshagent(['github-jenkins']) {
                  sh("git clone git@github.com:nationalarchives/tdr-configurations.git")
                }
                withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_TOKEN')]) {
                  sh 'terraform init'
                  //If Terraform workspace exists continue
                  sh "terraform workspace new ${terraformWorkspace} || true"
                  sh "terraform workspace select ${terraformWorkspace}"
                }
              }
            }
          }
          stage('Run Terraform plan') {
            steps {
              dir("${config.terraformDirectoryPath}") {
                echo 'Running Terraform plan...'
                withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_TOKEN')]) {
                  sh 'terraform plan'
                }
                script {
                  tdr.postToDaTdrSlackChannel(colour: "good",
                    message: "Terraform plan complete for ${config.stage} TDR ${config.deployment}. " +
                        "${env.BUILD_URL}console"
                  )
                }
              }
            }
          }
          stage('Approve Terraform plan') {
            steps {
              echo 'Sending request for approval of Terraform plan...'
              script {
                tdr.postToDaTdrSlackChannel(colour: "good",
                  message: "Do you approve Terraform deployment for ${config.stage} TDR ${config.deployment}? " +
                    "${env.BUILD_URL}input"
                )
              }
              input "Do you approve deployment to ${config.stage}?"
            }
          }
          stage('Apply Terraform changes') {
            steps {
              dir("${config.terraformDirectoryPath}") {
                echo 'Applying Terraform changes...'
                withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_TOKEN')]) {
                  sh 'echo "yes" | terraform apply'
                }
                echo 'Changes applied'
                script {
                  tdr.postToDaTdrSlackChannel(colour: "good",
                    message: "Deployment complete for ${config.stage} TDR ${config.deployment}"
                  )
                }
              }
            }
          }
          stage('Tag Release') {
            steps {
              //Terraform doesn't deploy using tags until https://national-archives.atlassian.net/browse/TDR-1229 is implemented
              //Ensure that the tagging between Jenkins intg and prod instances remain in sync by not tagging if deploying to staging and prod
              script {
                if (config.stage == "intg" || config.stage == "mgmt") {
                  sh "git tag ${versionTag}"
                  sshagent(['github-jenkins']) {
                    sh("git push origin ${versionTag}")
                  }
                }
              }
            }
          }
          stage("Update release branch") {
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
      }
    }
    post {
      cleanup {
        echo 'Deleting Jenkins workspace...'
        deleteDir()
      }
      success {
        script {
          if (config.stage == "intg" || config.stage == "staging") {
            int delaySeconds = config.testDelaySeconds

            tdr.runEndToEndTests(delaySeconds, config.stage, BUILD_URL)
          }
        }
      }
    }
  }
}
