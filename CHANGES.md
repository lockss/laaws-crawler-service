# `laaws-crawler-service` Release Notes


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



