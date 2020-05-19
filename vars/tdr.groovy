def buildEndToEndTests(int delay, String buildJob, String[] params) {
    build(job: buildJob, parameters: params, quietPeriod: delay, wait: false)
}

def setBuildStatus(String status, String desc) {
    withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_ACCESS_TOKEN')]) {
        String sha = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
        String url = "https://api.github.com/repos/nationalarchives/tdr-transfer-frontend/statuses/${sha}"
        sh "curl -XPOST '${url}' -H 'Authorization: bearer ${env.GITHUB_ACCESS_TOKEN}' --data '{\"state\":\"${status}\",\"target_url\":\"${env.BUILD_URL}\",\"description\":\"${desc}\",\"context\":\"Jenkins TDR Front end test\"}'"
    }
}