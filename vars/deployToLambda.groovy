def call(Map config) {
    library("tdr-jenkinslib")
    pipeline {
        agent {
            label "master"
        }
        parameters {
            choice(name: "STAGE", choices: ["intg", "staging", "prod"], description: "The stage you are building the front end for")
            string(name: "TO_DEPLOY", description: "The git tag, branch or commit reference to deploy, e.g. '1'")
        }
        stages {
            stage("Deploy lambda") {
                agent {
                    ecs {
                        inheritFrom "aws"
                        taskrole "arn:aws:iam::${env.MANAGEMENT_ACCOUNT}:role/TDRJenkinsNodeLambdaRole${config.stage.capitalize()}"
                    }
                }
                steps {
                    script {
                        def accountNumber = tdr.getAccountNumberFromStage(config.stage)
                        sh "python3 /deploy_lambda_from_s3.py ${accountNumber} ${config.stage} tdr-${config.libraryName}-${config.stage} tdr-backend-code-mgmt ${config.version}/${config.libraryName}.jar"
                    }
                }
            }
        }
        post {
            success {
                script {
                    tdr.runEndToEndTests(0, config.stage, BUILD_URL)
                }
            }
        }
    }
}