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
                            //sh "docker pull nationalarchives/${params.imageName}:${params.toDeploy}"
                            //sh "docker tag nationalarchives/${params.imageName}:${params.toDeploy} nationalarchives/${params.imageName}:intg"
                            //sh "docker push nationalarchives/${params.imageName}:intg"

                            slackSend color: "good", message: "*${params.imageName}* :whale: The '${params.toDeploy}' image has been tagged with 'intg' in Docker Hub", channel: "#bot-testing"
                        }
                    }
                }
            }
            stage("Update ECS container") {
                agent {
                    ecs {
                        inheritFrom "aws"
                        taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/TDRJenkinsNodeRoleIntg"
                    }
                }
                steps {
                    script {                        
                        //sh "python3 /update_service.py ${params.accountNumber} ${params.STAGE} ${params.eCSService}"
                        slackSend color: "good", message: "*${params.eCSService}* :arrow_up: The app has been updated in ECS in the *intg* environment", channel: "#bot-testing"
                    }
                }
            }
        }
        post {
            success {
                script {
                    //tdr.runEndToEndTests(300, params.STAGE, BUILD_URL)
                    echo "${BUILD_URL}"
                }
            }
        }
    }
}