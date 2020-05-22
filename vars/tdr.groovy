// Call when deployment (intg or staging) has finished. Use the delay to ensure that the AWS load balancer allows access to the new version you are deploying.
def runEndToEndTests(int delaySeconds, String stage) {
  String[] params = [
    string(name: "STAGE", value: stage)
  ]
  build(job: "TDRAcceptanceTest", parameters: params, quietPeriod: delaySeconds, wait: false)
}

//It is important for TDR devs to know that the code they want to merge doesn't break TDR. By sending the build status for every commit (all branches) to GitHub we can ensure code that breaks TDR cannot be merged.

//This method should be called at the start of a Jenkins build to let dev know changes are being checked.
//Should also be called as a 'post' pipeline action in Jenkins for both success and failure of build so that GitHub/devs know that build has completed successfully or failed.
def reportBuildStatusToGitHub(String repo, String buildStatus, String buildDesc) {
  withCredentials([string(credentialsId: 'github-jenkins-api-key', variable: 'GITHUB_ACCESS_TOKEN')]) {
    String sha = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    String url = "https://api.github.com/repos/nationalarchives/${repo}/statuses/${sha}"
    sh "curl -XPOST '${url}' -H 'Authorization: bearer ${env.GITHUB_ACCESS_TOKEN}' --data '{\"state\":\"${buildStatus}\",\"target_url\":\"${env.BUILD_URL}\",\"description\":\"${buildDesc}\",\"context\":\"TDR Jenkins build status\"}'"
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