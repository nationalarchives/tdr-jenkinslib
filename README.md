# TDR Jenkinslib

This is a library of groovy functions for the TDR Jenkins set up.

As TDR is a multi-faceted project dealing with many Jenkins builds, a shared library of functions limits the amount of repetitive code.
It allows changes to code within the shared library to affect all Jenkins jobs that use that function rather than having to change individual functions in each Jenkins job.
Most TDR multi-branch pipelines will use the functions from this Jenkinslib.

## Jenkins Configuration for Library Functions

TDR Jenkins has been configured to use the library functions with [Docker](https://github.com/nationalarchives/tdr-jenkins)

## Available functions

| Function | Parameters | Description | Result | 
|---|---|---|---|
| runEndToEndTests | delaySeconds, stage  | Triggers the [E2E](https://github.com/nationalarchives/tdr-e2e-tests) tests to run. This should be used after any changes are made to projects that affect TDR.  | No output, triggers the E2E Jenkins job.  |
| reportStartOfBuildToGitHub  | repo | Communicates the start of Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| reportSuccessfulBuildToGitHub| repo | Communicates successful completion of the Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| reportFailedBuildToGitHub| repo | Communicates failure of the Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| getAccountNumberFromStage| stage | Uses the stage that is being built to get AWS environment account number. This allows us to pull/push the correct info to/from AWS with the correct permissions. | AWS environment account number |
| githubApiStatusUrl| repo | Helper function to create GitHub API URL for the specified repository. | returns GitHub repository API URL |
