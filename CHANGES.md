# `laaws-crawler-service` Release Notes

## 1.2.0 (LOCKSS 2.0.91-beta2)

### Features

* Create Python client
* Prevent bug when a class is a prefix of a longer class

### API Changes

* Rename REST endpoint path `/crawls/{jobId}/notModified` to `/crawls/{jobId}/notmodified`
* Rename REST endpoint paths `normalizeUrl` to `normalizeurl` and `mimeType` to `mediatypes`; update associated code references, tests, and Swagger specifications
* Isolate `crawlKindEnum` out of `crawlDesc`, used in the request body of `POST /jobs`
* Change `priority` to nullable with a default of null to match the expected default in code
* Update `PageInfo` spec to specify some properties are nullable; rename `resultsPerPage` to `itemsInPage`
* Fix `PageInfo` imports


## Changes Since 1.0.0

* Remove  Travis CI
* Move to OpenAPI 3
* Move to Java 17
* Prevent JMS broker from being created automatically in tests that want to create one manually
* Use jakarta HttpServletRequest


### API Changes
* Remove delete crawl from crawlsApi and add delete and get to jobsApi
* Fix test code to match api change.
* Add missing PlatformConfigStatus



# 1.0.0
* First Release