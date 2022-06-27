/*
 * Copyright (C) 2022 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.gradle.api.internal.tasks.TaskDependencyContainer

plugins {
  `java-platform`
  id("org.projectnessie.buildsupport.ide-integration")
  `tools-integrations-conventions`
}

val versionAssertJ = "3.23.1"
val versionCheckstyle = "10.3.1"
val versionErrorProneAnnotations = "2.14.0"
val versionErrorProneCore = "2.14.0"
val versionErrorProneSlf4j = "0.1.12"
val versionGoogleJavaFormat = "1.15.0"
val versionGuava = "31.1-jre"
val versionJandex = "2.4.2.Final"
val versionJunit = "5.8.2"
val versionLogback = "1.2.11"
val versionOpenapi = "3.0"
val versionScala2_12 = "2.12.13"
val versionScala2_13 = "2.13.8"
val versionSlf4j = "1.7.36"
val versionSpark31 = "3.1.2"
val versionSpark32 = "3.2.1"

extra["versionCheckstyle"] = versionCheckstyle

extra["versionErrorProneAnnotations"] = versionErrorProneAnnotations

extra["versionErrorProneCore"] = versionErrorProneCore

extra["versionErrorProneSlf4j"] = versionErrorProneSlf4j

extra["versionGoogleJavaFormat"] = versionGoogleJavaFormat

extra["versionJandex"] = versionJandex

extra["versionScala2_12"] = versionScala2_12

extra["versionScala2_13"] = versionScala2_13

extra["versionSpark31"] = versionSpark31

extra["versionSpark32"] = versionSpark32

dependencies {
  constraints {
    api("ch.qos.logback:logback-access:$versionLogback")
    api("ch.qos.logback:logback-classic:$versionLogback")
    api("ch.qos.logback:logback-core:$versionLogback")
    api("com.google.errorprone:error_prone_annotations:$versionErrorProneAnnotations")
    api("com.google.errorprone:error_prone_core:$versionErrorProneCore")
    api("com.google.googlejavaformat:google-java-format:$versionGoogleJavaFormat")
    api("com.google.guava:guava:$versionGuava")
    api("com.puppycrawl.tools:checkstyle:$versionCheckstyle")
    api("jp.skypencil.errorprone.slf4j:errorprone-slf4j:$versionErrorProneSlf4j")
    api("org.assertj:assertj-core:$versionAssertJ")
    api("org.eclipse.microprofile.openapi:microprofile-openapi-api:$versionOpenapi")
    api("org.jboss:jandex:$versionJandex")
    api("org.junit:junit-bom:$versionJunit")
    api("org.slf4j:jcl-over-slf4j:$versionSlf4j")
    api("org.slf4j:log4j-over-slf4j:$versionSlf4j")
    api("org.slf4j:slf4j-api:$versionSlf4j")
  }
}

javaPlatform { allowDependencies() }

tasks.named<Wrapper>("wrapper") { distributionType = Wrapper.DistributionType.ALL }

tasks.register("publishLocal") {
  group = "publishing"
  description =
    "Bundles all publishToLocalMaven tasks from the included Nessie build, and its included Iceberg build"

  val inclBuild = gradle.includedBuild("nessie")
  val inclBuildInternal = inclBuild as org.gradle.internal.composite.IncludedBuildInternal
  val inclBuildTarget = inclBuildInternal.target
  inclBuildTarget.ensureProjectsLoaded()
  inclBuildTarget.projects.allProjects.forEach {
    val taskDependency =
      inclBuild.task("${it.projectPath}:publishToMavenLocal") as TaskDependencyContainer
    val lenientTaskDep = TaskDependencyContainer { context ->
      try {
        taskDependency.visitDependencies(context)
      } catch (x: Exception) {
        logger.debug("Ignoring lazy included-build task dependency {}", x.toString())
      }
    }
    dependsOn(lenientTaskDep)
  }
  dependsOn(inclBuild.task(":publishLocal"))
}
