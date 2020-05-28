## TDR Jenkinslib

This is a library of groovy functions for the TDR Jenkins set up.

As TDR is a multi-faceted project dealing with many Jenkins builds, a shared library of functions for Jenkins means we can limit the amount of repetitive code. It also allows devs to change code in the Jenkinslib and affect all Jenkins jobs that use that function rather than having to change functions in each Jenkins pipeline.

### Jenkins projects using the Jenkinslib

[E2E tests](https://github.com/nationalarchives/tdr-e2e-tests) should be triggered after:
* An (integration for now) deployment has successfully built
* An API deployment has successfully built
* If the terraform environment changes
* If there are any changes to the DB.

Jenkins build status posts to Github when:
* There are changes to the TDR FE project (all branches)
* When the API has changed
* When DB has changed

### Set up with Jenkins

TDR Jenkins has been set up using [Docker](https://github.com/nationalarchives/tdr-jenkins)

### Available functions

| Function | Parameters | Description | Result | 
|---|---|---|---|
| runEndToEndTests | delay period(seconds), build stage  | Triggers the [E2E](https://github.com/nationalarchives/tdr-e2e-tests) tests to run. This should be used after any changes are made to projects that affect TDR.  | No output, triggers the E2E Jenkins job.  |
| reportStartOfBuildToGitHub  | repo | Communicates the beginning of Jenkins build on Jenkins test pipeline. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POST's build info to the GitHub API  |
| reportSuccessfulBuildToGitHub| repo | Communicates the build status of a Jenkins pipeline to Github. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POST's build info to the GitHub API  |
| reportFailedBuildToGitHub| repo | Communicates the build status of a Jenkins pipeline to Github. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POST's build info to the GitHub API  |
| githubApiStatusUrl| repo | Helper for the status functions to create GitHub API URL to post status to correct repo | returns API URL |
