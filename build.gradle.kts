plugins {
  `java-library`
  `maven-publish`
  id("com.diffplug.spotless") version "7.0.4"
}

group = "com.splunk.ibm.mq"
version = "0.1.0"
description = "ibm-mq-monitoring"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
  gradlePluginPortal()
  mavenLocal()
  maven {
    url = uri("https://repo.maven.apache.org/maven2/")
  }
}

dependencies {
  api(libs.com.google.code.findbugs.jsr305)
  api(libs.org.jetbrains.annotations)
  api(libs.com.ibm.mq.com.ibm.mq.allclient)
  api(libs.org.yaml.snakeyaml)
  api(libs.com.fasterxml.jackson.core.jackson.databind)
  api(libs.io.opentelemetry.opentelemetry.sdk)
  api(libs.io.opentelemetry.opentelemetry.exporter.otlp)
  api(libs.io.opentelemetry.opentelemetry.sdk.extension.autoconfigure)
  api(libs.org.slf4j.slf4j.api)
  api(libs.org.apache.logging.log4j.log4j.api)
  api(libs.org.apache.logging.log4j.log4j.core)
  api(libs.org.apache.logging.log4j.log4j.slf4j.impl)
  api(libs.org.json.json)
  testImplementation(libs.org.junit.jupiter.junit.jupiter.engine)
  testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.mockito.mockito.junit.jupiter)
  testImplementation(libs.org.assertj.assertj.core)
  testImplementation(libs.io.opentelemetry.opentelemetry.sdk.testing)
  testImplementation(libs.com.ibm.mq.com.ibm.mq.jakarta.client)
  testImplementation(libs.jakarta.jms.jakarta.jms.api)
}

publishing {
  publications.create<MavenPublication>("maven") {
    from(components["java"])
  }
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("buildscripts/spotless.license.java"))
    target("src/**/*.java")
  }
  kotlinGradle {
    ktlint().editorConfigOverride(
      mapOf(
        "indent_size" to "2",
        "continuation_indent_size" to "2",
        "max_line_length" to "160",
        "insert_final_newline" to "true",
        "ktlint_standard_no-wildcard-imports" to "disabled",
        // ktlint does not break up long lines, it just fails on them
        "ktlint_standard_max-line-length" to "disabled",
        // ktlint makes it *very* hard to locate where this actually happened
        "ktlint_standard_trailing-comma-on-call-site" to "disabled",
        // depends on ktlint_standard_wrapping
        "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
        // also very hard to find out where this happens
        "ktlint_standard_wrapping" to "disabled"
      )
    )
  }
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
  options.encoding = "UTF-8"
}
