# TDR Jenkinslib

This is a library of groovy functions for the TDR Jenkins set up.

As TDR is a multi-faceted project dealing with many Jenkins builds, a shared library of functions limits the amount of repetitive code.
It allows changes to code within the shared library to affect all Jenkins jobs that use that function rather than having to change individual functions in each Jenkins job.
Most TDR multi-branch pipelines will use the functions from this Jenkinslib.

## Jenkins Configuration for Library Functions

TDR Jenkins has been configured to use the library functions with [Docker](https://github.com/nationalarchives/tdr-jenkins)

## Available functions

| File | Function | Parameters | Description | Result |
|---|---|---|---|---|
| ecsDeployJob | call | *config map*: imageName, toDeploy, stage, ecsService, testDelaySecond | Standard TDR Jenkins pipeline job for ECS deployments. Called by client Jenkins jobs | No output, deploys to ECS |
| sbtReleaseDeployJob | call | *config map*: libraryName, buildNumber, repo| Standard TDR Jenkins pipeline job for sbt library release and deployment. Called by client Jenkins jobs | No output. Publishes updated sbt library to S3 |
| tdr | runEndToEndTests | delaySeconds, stage  | Triggers the [E2E](https://github.com/nationalarchives/tdr-e2e-tests) tests to run. This should be used after any changes are made to projects that affect TDR.  | No output, triggers the E2E Jenkins job.  |
| tdr | reportStartOfBuildToGitHub  | repo | Communicates the start of Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| tdr | reportSuccessfulBuildToGitHub| repo | Communicates successful completion of the Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| tdr | reportFailedBuildToGitHub| repo | Communicates failure of the Jenkins build job for the specified GitHub repository. This is an important aspect to making sure code that breaks TDR is not then merged into the project and deployed.  | POSTs build info to the GitHub API  |
| tdr | getAccountNumberFromStage| stage | Uses the stage that is being built to get AWS environment account number. This allows us to pull/push the correct info to/from AWS with the correct permissions. | AWS environment account number |
| tdr | githubApiStatusUrl| repo | Helper function to create GitHub API URL for the specified repository. | returns GitHub repository API URL |
| tdr | incrementLatestTag | None | Helper function that retrieves the latest Git Tag and increments by 1 | Returns tag number incremented by 1 |
| terraformCheckJob | call | *config map*: repo, terraformDirectoryPath *(relative location of the Terraform root file)* | Standard TDR Jenkins pipeline for running `terraform fmt -check` command. Called by client Jenkins jobs | No output, deploys runs `terraform fmt -check` command. |
| terraformDeployJob | call | *config map*: stage, repo, taskRoleName *(IAM role with permission to create AWS resources)*, deployment *(what is being deployed, eg Grafana, TDR environment)*, terraformDirectoryPath *(relative location of the Terraform root file)*, testDelaySeconds | Standard TDR Jenkins pipeline for deploying Terraform. Called by client Jenkins jobs | No output, deploys Terraform. |

## Testing functions on Jenkins

1. Create a branch with the new function(s) on in the tdr-jenkinslib repo;
2. In the Jenkins file that calls the new function(s) add the branch import directly: `@Library("tdr-jenkinslib@name-of-branch") _`
3. Create a test pipeline job in Jenkins
4. If the project is a multibranch pipeline, in the pipeline config add the library config to the Pipeline Libraries. Set the default to the name of the branch with the function(s) to test.
5. When you replay a branch on the pipeline, all the code from the Jenkins file AND the library is available for editing

If the job is a regular pipeline job rather than a multibranch pipeline, you can still test the Jenkinslib branch with `@Library("tdr-jenkinslib@name-of-branch") _`, but you cannot edit the library when replaying the build. You will have to make changes on the Jenkinslib branch and push them. Alternatively, you can create a multibranch pipeline job just for test purposes, but you should add a "Filter by name" filter in the Branch Sources section of the Jenkins job config so that the job only builds your branch.

## Useful documentation

* https://www.jenkins.io/doc/book/pipeline/shared-libraries/
