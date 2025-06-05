import org.gradle.kotlin.dsl.provideDelegate

plugins {
  `java-library`
  `maven-publish`
  id("com.diffplug.spotless") version "7.0.4"
  id("com.gradleup.shadow") version "9.0.0-beta15"
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

sourceSets {
  create("integrationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
  }
}

val integrationTestImplementation by configurations.getting {
  extendsFrom(configurations.implementation.get())
}
val integrationTestRuntimeOnly by configurations.getting

configurations["integrationTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val ibmClientJar: Configuration by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
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
  testImplementation(libs.org.junit.jupiter.junit.jupiter.api)
  testImplementation(libs.org.junit.jupiter.junit.jupiter.params)
  testImplementation(libs.org.mockito.mockito.core)
  testImplementation(libs.org.mockito.mockito.junit.jupiter)
  testImplementation(libs.org.assertj.assertj.core)
  testImplementation(libs.io.opentelemetry.opentelemetry.sdk.testing)
  testImplementation(libs.com.ibm.mq.com.ibm.mq.jakarta.client)
  testImplementation(libs.jakarta.jms.jakarta.jms.api)
  testImplementation(libs.org.junit.jupiter.junit.jupiter.engine)
  testRuntimeOnly(libs.org.junit.platform.junit.platform.launcher)
  integrationTestImplementation(libs.org.assertj.assertj.core)
  integrationTestImplementation(libs.org.junit.jupiter.junit.jupiter.api)
  integrationTestImplementation(libs.io.opentelemetry.opentelemetry.sdk.testing)
  integrationTestImplementation(libs.com.ibm.mq.com.ibm.mq.jakarta.client)
  integrationTestImplementation(libs.jakarta.jms.jakarta.jms.api)
  integrationTestImplementation(libs.org.junit.jupiter.junit.jupiter.engine)
  integrationTestRuntimeOnly(libs.org.junit.platform.junit.platform.launcher)
  ibmClientJar(libs.com.ibm.mq.com.ibm.mq.allclient) {
    artifact {
      name = "com.ibm.mq.allclient"
      extension = "jar"
    }
    isTransitive = false
  }
}

tasks.shadowJar {
  dependencies {
    exclude(dependency(libs.com.ibm.mq.com.ibm.mq.allclient))
  }
}

tasks.test {
  useJUnitPlatform()
}

tasks {
// This exists purely to get the extension jar into our build dir
  val copyIbmClientJar by registering(Jar::class) {
    archiveFileName.set("com.ibm.mq.allclient.jar")
    doFirst {
      from(zipTree(ibmClientJar.singleFile))
    }
  }
}

val integrationTest = tasks.register<Test>("integrationTest") {
  description = "Runs integration tests."
  group = "verification"

  testClassesDirs = sourceSets["integrationTest"].output.classesDirs
  classpath = sourceSets["integrationTest"].runtimeClasspath
  shouldRunAfter("test")

  useJUnitPlatform()

  testLogging {
    events("passed")
  }
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
