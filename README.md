# TDR Jenkinslib

This is a library of groovy functions for the TDR Jenkins set up.

As TDR is a multi-faceted project dealing with many Jenkins builds, a shared library of functions limits the amount of repetitive code.
It allows changes to code within the shared library to affect all Jenkins jobs that use that function rather than having to change individual functions in each Jenkins job.
Most TDR multi-branch pipelines will use the functions from this Jenkinslib.

## Jenkins Configuration for Library Functions

TDR Jenkins has been configured to use the library functions with [Docker](https://github.com/nationalarchives/tdr-jenkins)

## Available functions

File | Function | Parameters | Description | Result | 
|---|---|---|---|---|
| ecsDeployJob | call | config map: imageName, toDeploy, stage, ecsService, testDelaySecond | Standard Jenkins pipeline job for ECS deployments. Called by client Jenkins jobs | No output, deploys to ECS |
| sbtReleaseDeployJob | call | config map: libraryName, buildNumber, repo| Standard Jenkins pipeline job for sbt library release and deployment | No output. Publishes updated sbt library to S3 |
| tdr | runEndToEndTests | delaySeconds, stage  | Triggers the [E2E](https://github.com/nationalarchives/tdr-e2e-tests) tests to run. This should be used after any changes are made to projects that affect TDR.  | No output, triggers the E2E Jenkins job.  |
| tdr | reportStartOfBuildToGitHub  | repo | Communicates the start of Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| tdr | reportSuccessfulBuildToGitHub| repo | Communicates successful completion of the Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| tdr | reportFailedBuildToGitHub| repo | Communicates failure of the Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| tdr | getAccountNumberFromStage| stage | Uses the stage that is being built to get AWS environment account number. This allows us to pull/push the correct info to/from AWS with the correct permissions. | AWS environment account number |
| tdr | githubApiStatusUrl| repo | Helper function to create GitHub API URL for the specified repository. | returns GitHub repository API URL |

## Testing functions on Jenkins

1. Create a branch with the new function(s) on in the tdr-jenkinslib repo;
2. In the Jenkins file that calls the new function(s) add the branch import directly: `@Library("tdr-jenkinslib@[name of branch]") _` 
3. Create a test multi-branch pipeline job in Jenkins
4. In the pipeline config add the library config to the Pipeline Libraries. Set the default to the name of the branch with the function(s) to test.
5. When you replay a branch on the pipeline, all the code from the Jenkins file AND the library is available for editing


