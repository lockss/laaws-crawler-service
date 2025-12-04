/*
 * LAAWS Crawler Service
 *
 * LOCKSS Crawler Service providing REST API for content crawling.
 */

plugins {
    id("lockss-spring-boot-conventions")
}

group = "org.lockss.laaws"
version = "2.8.0-SNAPSHOT"
description = "LOCKSS Crawler Service"

// OpenAPI code generation configuration
openapi {
    specFile.set(file("src/main/resources/swagger/swagger.yaml"))
    basePackage.set("org.lockss.laaws.crawler")
}

dependencies {
    // Internal dependencies
    api(project(":lockss-spring-bundle"))

    // Nitrite embedded database
    api("org.dizitart:nitrite:3.4.4") {
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-annotations")
    }

    // Jackson datatypes
    api(libs.jackson.datatype.jsr310)

    // SpringDoc OpenAPI (for generated code)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Test dependencies
    testImplementation(platform(project(":lockss-pom-bundles:lockss-junit5-bundle")))
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":lockss-spring-bundle", configuration = "testArtifacts"))
    testImplementation(project(":lockss-core", configuration = "testArtifacts"))
}

// Docker configuration
docker {
    imageName.set("laaws-crawler-service")
    restPort.set(24640)
    uiPort.set(24641)
}
