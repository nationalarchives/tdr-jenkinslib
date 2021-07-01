def call(Map config) {
  library("tdr-jenkinslib")

  def terraformWorkspace = config.stage == "mgmt" ? "default" : config.stage
  def terraformModulesBranch = config.containsKey("terraformNode") ? config.terraformNode : "master"
  def terraformNode = config.containsKey("terraformNode") ? config.terraformNode : "terraform"
  def versionTag = "v${env.BUILD_NUMBER}"

  pipeline {
    agent {
      label "master"
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
                sh 'terraform init'
                //If Terraform workspace exists continue
                sh "terraform workspace new ${terraformWorkspace} || true"
                sh "terraform workspace select ${terraformWorkspace}"
              }
            }
          }
          stage('Run Terraform plan') {
            steps {
              dir("${config.terraformDirectoryPath}") {
                echo 'Running Terraform plan...'
                sh 'terraform plan'
                script {
                  tdr.postToDaTdrSlackChannel(colour: "good",
                    message: "Terraform plan complete for ${config.stage} TDR ${config.deployment}. " +
                      "View here for plan: https://jenkins.tdr-management.nationalarchives.gov.uk/job/" +
                      "${JOB_NAME.replaceAll(' ', '%20')}/${BUILD_NUMBER}/console"
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
                    "https://jenkins.tdr-management.nationalarchives.gov.uk/job/" +
                    "${JOB_NAME.replaceAll(' ', '%20')}/${BUILD_NUMBER}/input/"
                )
              }
              input "Do you approve deployment to ${config.stage}?"
            }
          }
          stage('Apply Terraform changes') {
            steps {
              dir("${config.terraformDirectoryPath}") {
                echo 'Applying Terraform changes...'
                sh 'echo "yes" | terraform apply'
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
              sh "git tag ${versionTag}"
              sshagent(['github-jenkins']) {
                sh("git push origin ${versionTag}")
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
