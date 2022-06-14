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

import com.github.vlsi.jandex.JandexProcessResources
import java.io.File
import java.util.*
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.exclude
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.withType

/**
 * Apply the given `sparkVersion` as a `strictly` version constraint and [withSparkExcludes] on the
 * current [Dependency].
 */
fun ModuleDependency.forSpark(sparkVersion: String): ModuleDependency {
  val dep = this as ExternalModuleDependency
  dep.version { strictly(sparkVersion) }
  return this.withSparkExcludes()
}

/** Apply a bunch of common dependency-exclusion to the current Spark [Dependency]. */
fun ModuleDependency.withSparkExcludes(): ModuleDependency {
  return this.exclude("commons-logging", "commons-logging")
    .exclude("log4j", "log4j")
    .exclude("org.slf4j", "slf4j-log4j12")
    .exclude("org.eclipse.jetty", "jetty-util")
    .exclude("org.apache.avro", "avro")
    .exclude("org.apache.arrow", "arrow-vector")
}

fun DependencyHandlerScope.forScala(scalaVersion: String) {
  // Note: Quarkus contains Scala dependencies since 2.9.0
  add("implementation", "org.scala-lang:scala-library") { version { strictly(scalaVersion) } }
  add("implementation", "org.scala-lang:scala-reflect") { version { strictly(scalaVersion) } }
}

/**
 * Forces all [Test] tasks to use Java 11 for test execution, which is mandatory for tests using
 * Spark.
 */
fun Project.forceJava11ForTests() {
  if (!JavaVersion.current().isJava11) {
    tasks.withType(Test::class.java).configureEach {
      val javaToolchains = project.extensions.findByType(JavaToolchainService::class.java)
      javaLauncher.set(
        javaToolchains!!.launcherFor { languageVersion.set(JavaLanguageVersion.of(11)) }
      )
    }
  }
}

fun Project.dependencyVersion(key: String) = rootProject.extra[key].toString()

fun Project.testLogLevel() = System.getProperty("test.log.level", "WARN")

/** Just load [Properties] from a [File]. */
fun loadProperties(file: File): Properties {
  val props = Properties()
  file.reader().use { reader -> props.load(reader) }
  return props
}

/** Hack for Jandex-Plugin (removed later). */
fun Project.useBuildSubDirectory(buildSubDir: String) {
  buildDir = file("$buildDir/$buildSubDir")

  // TODO open an issue for the Jandex plugin - it configures the task's output directory too
  //  early, so re-assigning the output directory (project.buildDir=...) to a different path
  //  isn't reflected in the Jandex output.
  tasks.withType<JandexProcessResources>().configureEach {
    val sourceSets: SourceSetContainer by project
    sourceSets.all { destinationDir = this.output.resourcesDir!! }
  }
}

/** Resolves the Spark and Scala major versions for all `nqeit-iceberg-spark*` projects. */
fun Project.getSparkScalaVersionsForProject(): SparkScalaVersions {
  val sparkScala = project.name.split("-").last().split("_")

  val sparkMajorVersion = if (sparkScala[0][0].isDigit()) sparkScala[0] else "3.2"
  val scalaMajorVersion = sparkScala[1]

  useBuildSubDirectory("$sparkMajorVersion-$scalaMajorVersion")

  return useSparkScalaVersionsForProject(sparkMajorVersion, scalaMajorVersion)
}

fun Project.useSparkScalaVersionsForProject(
  sparkMajorVersion: String,
  scalaMajorVersion: String
): SparkScalaVersions {
  return SparkScalaVersions(
    sparkMajorVersion,
    scalaMajorVersion,
    dependencyVersion("versionSpark-$sparkMajorVersion"),
    dependencyVersion("versionScala-$scalaMajorVersion")
  )
}

class SparkScalaVersions(
  val sparkMajorVersion: String,
  val scalaMajorVersion: String,
  val sparkVersion: String,
  val scalaVersion: String
) {}

/** Resolves the Flink and Scala major versions for all `nqeit-iceberg-flink*` projects. */
fun Project.getFlinkVersionsForProject(): FlinkVersions {
  val flinkMajorVersion = project.name.split("-").last()
  val scalaMajorVersion = dependencyVersion("flink-scala-$flinkMajorVersion")
  val scalaForDependencies = dependencyVersion("flink-scalaForDependencies-$flinkMajorVersion")

  useBuildSubDirectory(flinkMajorVersion)

  return useFlinkVersionsForProject(flinkMajorVersion, scalaMajorVersion, scalaForDependencies)
}

fun Project.useFlinkVersionsForProject(
  flinkMajorVersion: String,
  scalaMajorVersion: String,
  scalaForDependencies: String
): FlinkVersions {
  return FlinkVersions(
    flinkMajorVersion,
    scalaMajorVersion,
    scalaForDependencies,
    dependencyVersion("versionFlink-$flinkMajorVersion"),
    "2.7.3"
  )
}

class FlinkVersions(
  val flinkMajorVersion: String,
  val scalaMajorVersion: String,
  val scalaForDependencies: String,
  val flinkVersion: String,
  val hadoopVersion: String
) {}

/** Resolves the Presto versions for all `nqeit-presto*` projects. */
fun Project.getPrestoVersionsForProject(): PrestoVersions {
  val prestoMajorVersion = project.name.split("-").last()

  useBuildSubDirectory(prestoMajorVersion)

  return usePrestoVersionsForProject(prestoMajorVersion)
}

fun Project.usePrestoVersionsForProject(prestoMajorVersion: String): PrestoVersions {
  return PrestoVersions(prestoMajorVersion, dependencyVersion("versionPresto-$prestoMajorVersion"))
}

class PrestoVersions(val prestoMajorVersion: String, val prestoVersion: String) {}
