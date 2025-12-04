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

dependencies {
    // Internal dependencies
    api(project(":lockss-spring-bundle"))

    // PostgreSQL
    api(libs.postgresql)

    // Test dependencies
    testImplementation(platform(project(":lockss-pom-bundles:lockss-junit5-bundle")))
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.embedded.postgres)
}

// Docker configuration
docker {
    imageName.set("laaws-crawler-service")
    restPort.set(24640)
    uiPort.set(24641)
}
