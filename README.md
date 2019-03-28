# Jenkins QRebel Plugin

## What is Jenkins QRebel Plugin?
This Jenkins Plugin collects application performance data from [QRebel](https://qrebel.com/) and marks the build as failed if its performance degrades.

## How to use it?
### Attach QRebel agent
Attach the agent to existing tests to start monitoring your application performance. See [QRebel Quick Start](https://qrebel.com/quick-start/) guide. Configure your app name eg. `petclinic`. Label your app changes with builds eg. `1.4.0rc1`, `1.4.0rc2` etc.
### Add a QRebel post-build action
Add a new post-build action `Monitor performance with QRebel`. This action will compare the performance of a baseline build and a target build.   
* Type the configured app name to `Application name`, eg. `petclinic`
* Specify `Target build` eg. `1.4.0rc2`. Environment variables are supported eg. `1.4.0rc${BUILD_NUMBER}`
* Specify `Baseline build` - the initial build to compare your app performance with. Usually static. Eg. `1.4.rc1` If not specified, the comparison is performed against the static threshold.
* Provide `QRebel API Token ` - REST API authentication token, see [REST API](https://manuals.zeroturnaround.com/qrebel/api/index.html) for detail

If a build fails than the cause will be specified in its description, eg.
```
Failing build due to performance regressions found in petclinic compared to 1.4.0rc1.
Slow Requests: 15
Excessive IO: 0
Exceptions: 2 
SLA global limit (ms): 3770 ms | slowest endpoint time(ms): 3770 ms
For full report check your <a href="https://hub.qrebel.com/#/12345/petclinic/?baseline=1.4.0rc1&target=1.4.0rc2">dashboard</a>
```
More detail will appear if you click on `dashboard`