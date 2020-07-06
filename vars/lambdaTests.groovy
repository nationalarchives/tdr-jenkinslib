
def call(Map config) {
    library("tdr-jenkinslib")

    def versionTag = "v${env.BUILD_NUMBER}"
    def repo = "tdr-${config.libraryName}"

    pipeline {
        agent {
            label "master"
        }
        parameters {
            choice(name: "STAGE", choices: ["intg", "staging", "prod"], description: "The stage you are building the front end for")
        }
        stages {
            stage("Build") {
                agent {
                    ecs {
                        inheritFrom "transfer-frontend"
                    }
                }
                steps {
                    script {
                        tdr.reportStartOfBuildToGitHub(repo)
                        tdr.assembleAndStash(config.libraryName)
                    }
                }
            }
            stage('Post-build') {
                agent {
                    ecs {
                        inheritFrom "aws"
                        taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/TDRJenkinsNodeLambdaRole${config.stage.capitalize()}"
                    }
                }

                when {
                    expression { env.BRANCH_NAME == "master"}
                }

                stages {
                    stage('Deploy to integration') {
                        steps {
                            script {
                                unstash "${config.libraryName}-jar"
                                tdr.copyToS3CodeBucket(config.libraryName, versionTag)

                                tdr.configureJenkinsGitUser()

                                sh "git tag ${versionTag}"
                                sh "git checkout ${env.BRANCH_NAME}"

                                tdr.pushGitHubBranch("master")
                                build(
                                        job: "TDR ${config.libraryName.split("\\s+").collect { it.toLowerCase().capitalize()}} Lambda Deploy",
                                        parameters: [
                                                string(name: "STAGE", value: "intg"),
                                                string(name: "TO_DEPLOY", value: versionTag)
                                        ],
                                        wait: false)
                            }
                        }
                    }
                }
            }
        }
        post {
            failure {
                script {
                    tdr.reportFailedBuildToGitHub(repo)
                }
            }
            success {
                script {
                    tdr.reportSuccessfulBuildToGitHub(repo)
                }
            }
        }
    }

}