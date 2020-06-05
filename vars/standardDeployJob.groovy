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
                steps {
                    script {
                        docker.withRegistry('', 'docker') {
                            sh "docker pull nationalarchives/${params.imageName}:${params.toDeploy}"
                            sh "docker tag nationalarchives/${params.imageName}:${params.toDeploy} nationalarchives/${params.imageName}:${params.stage}"
                            sh "docker push nationalarchives/${params.imageName}:${params.stage}"

                            slackSend color: "good", message: "*${params.imageName}* :whale: The '${params.toDeploy}' image has been tagged with '${params.stage}' in Docker Hub", channel: "#tdr-releases"
                        }
                    }
                }
            }
            stage("Update ECS container") {
                agent {
                    ecs {
                        inheritFrom "aws"
                        taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/TDRJenkinsNodeRole${params.stage.capitalize()}"
                    }
                }
                steps {
                    script {
                        def accountNumber = tdr.getAccountNumberFromStage("${params.stage}")

                        sh "python3 /update_service.py ${accountNumber} ${params.stage} ${params.eCSService}"
                        slackSend color: "good", message: "*${params.eCSService}* :arrow_up: The app has been updated in ECS in the *${params.stage}* environment", channel: "#tdr-releases"
                    }
                }
            }
            stage("Update release branch") {
                agent {
                    label "master"
                }
                steps {
                    sh "git branch -f ${params.releaseBranch} HEAD"
                    sshagent(['github-jenkins']) {
                        sh("git push -f origin ${params.releaseBranch}")
                    }
                }
            }
        }
        post {
            success {
                script {
                    if (params.stage == "intg") {
                        tdr.runEndToEndTests(300, "${params.stage}", BUILD_URL)
                    }
                }
            }
        }
    }
}
