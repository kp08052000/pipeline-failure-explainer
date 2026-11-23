export interface SampleLog {
  id: string;
  label: string;
  log: string;
}

// Sanitised, representative Azure DevOps logs used for demos.
export const SAMPLE_LOGS: SampleLog[] = [
  {
    id: 'npm-eresolve',
    label: 'npm peer dependency conflict',
    log: `2026-11-20T09:14:02.1184Z ##[section]Starting: npm ci
2026-11-20T09:14:02.1190Z ==============================================================================
2026-11-20T09:14:02.1191Z Task         : Command line
2026-11-20T09:14:02.1192Z Version      : 2.250.1
2026-11-20T09:14:02.1301Z ==============================================================================
2026-11-20T09:14:02.5520Z Generating script.
2026-11-20T09:14:02.5590Z Script contents:
2026-11-20T09:14:02.5591Z npm install
2026-11-20T09:14:03.0012Z node v22.11.0, npm 10.9.0
2026-11-20T09:14:11.4473Z npm error code ERESOLVE
2026-11-20T09:14:11.4475Z npm error ERESOLVE unable to resolve dependency tree
2026-11-20T09:14:11.4476Z npm error
2026-11-20T09:14:11.4477Z npm error While resolving: policy-portal@1.4.0
2026-11-20T09:14:11.4478Z npm error Found: @angular/core@17.3.12
2026-11-20T09:14:11.4479Z npm error node_modules/@angular/core
2026-11-20T09:14:11.4480Z npm error   @angular/core@"^17.3.0" from the root project
2026-11-20T09:14:11.4481Z npm error
2026-11-20T09:14:11.4482Z npm error Could not resolve dependency:
2026-11-20T09:14:11.4483Z npm error peer @angular/core@"^18.0.0 || ^19.0.0" from @angular/cdk@18.2.14
2026-11-20T09:14:11.4484Z npm error node_modules/@angular/cdk
2026-11-20T09:14:11.4485Z npm error   @angular/cdk@"^18.2.0" from the root project
2026-11-20T09:14:11.4486Z npm error
2026-11-20T09:14:11.4487Z npm error Fix the upstream dependency conflict, or retry
2026-11-20T09:14:11.4488Z npm error this command with --force or --legacy-peer-deps
2026-11-20T09:14:11.4489Z npm error to accept an incorrect (and potentially broken) dependency resolution.
2026-11-20T09:14:11.5120Z ##[error]Bash exited with code '1'.
2026-11-20T09:14:11.5201Z ##[section]Finishing: npm ci`,
  },
  {
    id: 'maven-tests',
    label: 'Maven unit test failure',
    log: `2026-11-21T13:02:44.0011Z ##[section]Starting: Maven build
2026-11-21T13:02:44.0101Z Task         : Maven
2026-11-21T13:02:44.0102Z Version      : 4.251.0
2026-11-21T13:03:20.7710Z [INFO] --- surefire:3.5.2:test (default-test) @ claims-service ---
2026-11-21T13:03:21.1022Z [INFO] Running com.example.claims.PremiumCalculatorTest
2026-11-21T13:03:21.6630Z [ERROR] Tests run: 12, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.531 s <<< FAILURE! -- in com.example.claims.PremiumCalculatorTest
2026-11-21T13:03:21.6632Z [ERROR] com.example.claims.PremiumCalculatorTest.roundsToTwoDecimals -- Time elapsed: 0.012 s <<< FAILURE!
2026-11-21T13:03:21.6633Z org.opentest4j.AssertionFailedError: expected: <1234.57> but was: <1234,57>
2026-11-21T13:03:21.6634Z 	at com.example.claims.PremiumCalculatorTest.roundsToTwoDecimals(PremiumCalculatorTest.java:48)
2026-11-21T13:03:22.0040Z [INFO] Results:
2026-11-21T13:03:22.0041Z [ERROR] Failures:
2026-11-21T13:03:22.0042Z [ERROR]   PremiumCalculatorTest.roundsToTwoDecimals:48 expected: <1234.57> but was: <1234,57>
2026-11-21T13:03:22.0043Z [ERROR] Tests run: 87, Failures: 1, Errors: 0, Skipped: 2
2026-11-21T13:03:22.1180Z [INFO] BUILD FAILURE
2026-11-21T13:03:22.1190Z [ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.2:test (default-test) on project claims-service: There are test failures.
2026-11-21T13:03:22.3301Z ##[error]Build failed.
2026-11-21T13:03:22.3402Z ##[section]Finishing: Maven build`,
  },
  {
    id: 'docker-auth',
    label: 'Docker push unauthorized',
    log: `2026-11-22T08:40:10.1000Z ##[section]Starting: Push image
2026-11-22T08:40:10.1100Z Task         : Docker
2026-11-22T08:40:10.1101Z Version      : 2.248.0
2026-11-22T08:40:10.9000Z /usr/bin/docker push myregistry.azurecr.io/claims-service:20261122.3
2026-11-22T08:40:11.2000Z The push refers to repository [myregistry.azurecr.io/claims-service]
2026-11-22T08:40:11.4000Z 5f70bf18a086: Preparing
2026-11-22T08:40:11.4001Z a3ed95caeb02: Preparing
2026-11-22T08:40:12.0100Z unauthorized: authentication required
2026-11-22T08:40:12.0500Z ##[error]The process '/usr/bin/docker' failed with exit code 1
2026-11-22T08:40:12.0600Z ##[section]Finishing: Push image`,
  },
  {
    id: 'insufficient',
    label: 'Vague failure (insufficient evidence)',
    log: `2026-11-23T10:05:31.0000Z ##[section]Starting: Build and package
2026-11-23T10:05:31.0100Z Task         : Bash
2026-11-23T10:05:31.0101Z Version      : 3.250.0
2026-11-23T10:05:31.2000Z Generating script.
2026-11-23T10:05:31.2100Z ========================== Starting Command Output ===========================
2026-11-23T10:05:31.2200Z /usr/bin/bash /home/vsts/work/_temp/build.sh
2026-11-23T10:07:02.5100Z ##[error]Bash exited with code '2'.
2026-11-23T10:07:02.5200Z ##[section]Finishing: Build and package`,
  },
];
