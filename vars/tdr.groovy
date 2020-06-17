// Call when deployment (intg or staging) has finished. Use the delay to ensure that the AWS load balancer allows access to the new version you are deploying.
def runEndToEndTests(int delaySeconds, String stage, String buildUrl) {
  build(
    job: "TDRAcceptanceTest",
    parameters: [
      string(name: "STAGE", value: stage),
      string(name: "DEPLOY_JOB_URL", value: buildUrl)
    ],
    quietPeriod: delaySeconds,
    wait: false)
}

//It is important for TDR devs to know that the code they want to merge doesn't break TDR. By sending the build status for every commit (all branches) to GitHub we can ensure code that breaks TDR cannot be merged.

// Call this when build starts (to let person who made changes know they are being checked) - call within first 'stage' of Jenkins pipeline actions.
def reportStartOfBuildToGitHub(String repo) {
  withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_ACCESS_TOKEN')]) {
    sh "curl -XPOST '${githubApiStatusUrl(repo)}' -H 'Authorization: bearer ${env.GITHUB_ACCESS_TOKEN}' --data '{\"state\":\"pending\",\"target_url\":\"${env.BUILD_URL}\",\"description\":\"Jenkins build has started\",\"context\":\"TDR Jenkins build status\"}'"
  }
}

// Call when build finishes successfully - in Jenkins pipeline 'post' actions
def reportSuccessfulBuildToGitHub(String repo) {
  withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_ACCESS_TOKEN')]) {
    sh "curl -XPOST '${githubApiStatusUrl(repo)}' -H 'Authorization: bearer ${env.GITHUB_ACCESS_TOKEN}' --data '{\"state\":\"success\",\"target_url\":\"${env.BUILD_URL}\",\"description\":\"Jenkins build has completed successfully\",\"context\":\"TDR Jenkins build status\"}'"
  }
}

// Call when build fails - in Jenkins pipeline 'post' actions
def reportFailedBuildToGitHub(String repo) {
  withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_ACCESS_TOKEN')]) {
    sh "curl -XPOST '${githubApiStatusUrl(repo)}' -H 'Authorization: bearer ${env.GITHUB_ACCESS_TOKEN}' --data '{\"state\":\"failure\",\"target_url\":\"${env.BUILD_URL}\",\"description\":\"Jenkins build has failed\",\"context\":\"TDR Jenkins build status\"}'"
  }
}

def getAccountNumberFromStage(String stage) {
  def stageToAccountMap = [
    "intg": env.INTG_ACCOUNT,
    "staging": env.STAGING_ACCOUNT,
    "prod": env.PROD_ACCOUNT
  ]
  return stageToAccountMap.get(stage)
}

// This is used to get the URL needed to send a POST request to the GitHub API to update the specified repo with the Jenkins build status. This returns the API URL.
def githubApiStatusUrl(String repo) {
  String sha = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
  String url = "https://api.github.com/repos/nationalarchives/${repo}/statuses/${sha}"
  return url
}

def postToSlack(Map params){
  slackSend color: "${params.colour}",
            message: "${params.message}",
            channel: "#bot-testing" //#da-tdr-releases will be the actual channel bot-testing is the test one
}