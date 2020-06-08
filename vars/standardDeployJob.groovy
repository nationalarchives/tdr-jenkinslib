def call(Map params) {
    pipeline {
        agent {
            label "master"
        }
        stages {
            stage("Docker") {
                agent {
                    label "master"
                }
                parameters {
                    choice(name: "STAGE", choices: ["intg", "staging"], description: "The stage you are building the auth server for")
                    string(name: "TO_DEPLOY", description: "The git tag, branch or commit reference to deploy, e.g. 'v123'")
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
                    sh "git branch -f ${params.RELEASE_BRANCH} HEAD"
                    sshagent(['github-jenkins']) {
                        sh("git push -f origin ${params.RELEASE_BRANCH}")
                    }
                }
            }
        }
        post {
            success {
                script {
                    if (params.STAGE == "intg") {
                        tdr.runEndToEndTests(300, "${params.STAGE}", BUILD_URL)
                    }
                }
            }
        }
    }
}
