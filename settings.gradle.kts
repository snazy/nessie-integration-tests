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

import java.util.regex.Pattern
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector

if (!JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_11)) {
  throw GradleException("Build requires Java 11")
}

val baseVersion = file("version.txt").readText().trim()

pluginManagement {
  // Cannot use a settings-script global variable/value, so pass the 'versions' Properties via
  // settings.extra around.
  val versions = java.util.Properties()
  val pluginIdPattern =
    java.util.regex.Pattern.compile("\\s*id\\(\"([^\"]+)\"\\) version \"([^\"]+)\"\\s*")
  settings.extra["nessieBuildTools.versions"] = versions

  plugins {

    // Note: this is NOT a real project but a hack for dependabot to manage the plugin versions.
    //
    // Background: dependabot only manages dependencies (incl Gradle plugins) in build.gradle[.kts]
    // files. It scans the root build.gradle[.kts] fila and those in submodules referenced in
    // settings.gradle[.kts].
    // But dependabot does not manage managed plugin dependencies in settings.gradle[.kts].
    // However, since dependabot is a "dumb search and replace engine", we can use a trick:
    // 1. Have this "dummy" build.gradle.kts file with all managed plugin dependencies.
    // 2. Add an `include()` to this build file in settings.gradle.kts, surrounded with an
    //    `if (false)`, so Gradle does _not_ pick it up.
    // 3. Parse this file in our settings.gradle.kts, provide a `ResolutionStrategy` to the
    //    plugin dependencies.

    val pulledVersions =
      file("gradle/dependabot/build.gradle.kts")
        .readLines()
        .map { line -> pluginIdPattern.matcher(line) }
        .filter { matcher -> matcher.matches() }
        .associate { matcher -> matcher.group(1) to matcher.group(2) }

    resolutionStrategy {
      eachPlugin {
        if (requested.version == null) {
          var pluginId = requested.id.id
          if (
            pluginId.startsWith("org.projectnessie.buildsupport.") ||
              pluginId == "org.projectnessie.smallrye-open-api"
          ) {
            pluginId = "org.projectnessie.buildsupport.spotless"
          }
          if (pulledVersions.containsKey(pluginId)) {
            useVersion(pulledVersions[pluginId])
          }
        }
      }
    }

    versions["versionErrorPronePlugin"] = pulledVersions["net.ltgt.errorprone"]
    versions["versionIdeaExtPlugin"] = pulledVersions["org.jetbrains.gradle.plugin.idea-ext"]
    versions["versionSpotlessPlugin"] = pulledVersions["com.diffplug.spotless"]
    versions["versionJandexPlugin"] = pulledVersions["com.github.vlsi.jandex"]
    versions["versionShadowPlugin"] = pulledVersions["com.github.johnrengelman.plugin-shadow"]
    versions["versionNessieBuildPlugins"] =
      pulledVersions["org.projectnessie.buildsupport.spotless"]
    versions["versionProjectnessiePlugin"] = pulledVersions["org.projectnessie"]

    // The project's settings.gradle.kts is "executed" before buildSrc's settings.gradle.kts and
    // build.gradle.kts.
    //
    // Plugin and important dependency versions are defined here and shared with buildSrc via
    // a properties file, and via an 'extra' property with all other modules of the Nessie build.
    //
    // This approach works fine with GitHub's dependabot as well
    val nessieBuildVersionsFile = file("build/nessieBuild/versions.properties")
    nessieBuildVersionsFile.parentFile.mkdirs()
    nessieBuildVersionsFile.outputStream().use {
      versions.store(it, "Nessie Build versions from settings.gradle.kts - DO NOT MODIFY!")
    }
  }

  repositories {
    mavenCentral() // prefer Maven Central, in case Gradle's repo has issues
    gradlePluginPortal()
    if (System.getProperty("withMavenLocal").toBoolean()) {
      mavenLocal()
    }
  }
}

gradle.rootProject {
  val prj = this
  val versions = settings.extra["nessieBuildTools.versions"] as java.util.Properties
  versions.forEach { k, v -> prj.extra[k.toString()] = v }
}

gradle.beforeProject {
  version = baseVersion
  group = "org.projectnessie.integrations-tools-tests"
}

// Make nessie.integrationsTesting.*SourceTree absolute paths
fun projectCanonicalPath(sub: String): File {
  val property = "nessie.integrationsTesting.${sub}SourceTree"
  val projectDir =
    rootProject.projectDir.resolve(System.getProperty(property, "./included-builds/$sub"))
  val canonicalDir = projectDir.canonicalFile
  if (System.getProperty("idea.sync.active").toBoolean()) {
    val additionalPropertiesDir = file("$canonicalDir/build")
    additionalPropertiesDir.mkdirs()
    val additionalPropertiesFile = file("$additionalPropertiesDir/additional-build.properties")
    val additionalProperties = java.util.Properties()
    System.getProperties()
      .filter { e -> e.key.toString().startsWith("nessie.") }
      .forEach { k, v -> additionalProperties.put(k, v) }
    additionalPropertiesFile.writer().use { writer ->
      additionalProperties.store(writer, "Generated")
    }
  }

  return canonicalDir
}

fun projectFromIncludedBuild(includedBuild: String, projectPath: String): ProjectComponentSelector {
  // TODO this is dirty, but does its job, which is to substitute dependencies to
  //  org.projectnessie:nessie-* to the projects built by the included Nessie build
  try {
    val inclBuild = gradle.includedBuild(includedBuild)
    val inclBuildInternal = inclBuild as org.gradle.internal.composite.IncludedBuildInternal
    val inclBuildTarget = inclBuildInternal.target
    val nessiePrj = inclBuildTarget.projects.getProject(org.gradle.util.Path.path(projectPath))
    val prjIdent = nessiePrj.componentIdentifier
    return DefaultProjectComponentSelector.newSelector(prjIdent)
  } catch (x: Exception) {
    x.printStackTrace()
    throw x
  }
}

fun DependencySubstitution.manageNessieProjectDependency(
  includedBuildDesc: String,
  substitutions: DependencySubstitutions
): Boolean {
  val req = requested
  if (req is ModuleComponentSelector && req.version.isEmpty()) {
    if (
      req.group == "org.projectnessie" &&
        req.module.startsWith("nessie") &&
        req.module != "nessie-antlr-runtime"
    ) {
      val module = if (req.module == "nessie") "" else req.module
      val targetBuild = if (req.module.startsWith("nessie-spark-")) "nessie-iceberg" else "nessie"
      val prx = projectFromIncludedBuild(targetBuild, ":$module")
      logger.info(
        "Substituting {}'s dependency to '{}:{}' as project '{}' in build '{}'",
        includedBuildDesc,
        req.group,
        req.module,
        prx.projectPath,
        prx.buildName
      )
      val target = if (prx.projectPath == ":") substitutions.platform(prx) else prx
      useTarget(target, "Managed via $includedBuildDesc")
      return true
    }
  }
  return false
}

val includeNessieBuild = System.getProperty("nessie.versionNessie") == null
val includeIcebergBuild = System.getProperty("nessie.versionIceberg") == null

val nessieSourceDir = projectCanonicalPath("nessie")
val icebergSourceDir = projectCanonicalPath("iceberg")

if (includeNessieBuild) {
  logger.lifecycle("Including 'Nessie' from $nessieSourceDir")
  includeBuild(nessieSourceDir) { name = "nessie" }
}

if (includeIcebergBuild) {
  logger.lifecycle("Including 'Iceberg' from $icebergSourceDir")
  includeBuild(icebergSourceDir) {
    name = "iceberg"

    val propertyPattern = Pattern.compile("\\s*(\\S+)\\s*=\\s*(\\S+)\\s*")!!

    // Iceberg's "dependency recommendation" stuff doesn't work when the Iceberg build is included
    // here. So parse Iceberg's versions.props file here and substitute the affected dependencies.
    val icebergVersions = mutableMapOf<String, String>()
    file(projectDir.resolve("versions.props")).forEachLine { line ->
      val m = propertyPattern.matcher(line.trim())
      if (m.matches()) {
        icebergVersions[m.group(1)] = m.group(2)
      }
    }
    // TODO These dependencies are pulled from
    //   'com.google.cloud:google-cloud-bom:0.164.0'
    // via
    //   'com.google.cloud:libraries-bom:24.1.0'
    // but that somehow doesn't work in this case with includedBuild + substituted dependencies
    icebergVersions["com.google.cloud:google-cloud-nio"] = "0.123.17"
    icebergVersions["com.google.cloud:google-cloud-storage"] = "2.2.2"

    // Replace dependencies in the "root" build with projects from the included build.
    // Here: substitute declared
    dependencySubstitution {
      listOf(
          "iceberg-api",
          "iceberg-bundled-guava",
          "iceberg-common",
          "iceberg-core",
          "iceberg-hive-metastore",
          "iceberg-nessie",
          "iceberg-parquet"
        )
        .forEach { moduleName ->
          substitute(module("org.apache.iceberg:$moduleName")).using(project(":$moduleName"))
        }
      // TODO needs to depend on the `shadow` configuration of `:iceberg-bundled-guava`, but that
      //  doesn't really work here :(
      // substitute(module("org.apache.iceberg:iceberg-bundled-guava")).withClassifier("shadow").using(project(":iceberg-bundled-guava"))
      listOf("3.1", "3.2").forEach { sparkVersion ->
        val scalaVersion = "2.12"
        listOf("spark", "spark-extensions", "spark-runtime").forEach { moduleName ->
          val fullName = "iceberg-$moduleName-${sparkVersion}_$scalaVersion"
          substitute(module("org.apache.iceberg:$fullName"))
            .using(project(":iceberg-spark:$fullName"))
        }
        if (sparkVersion == "3.1") {
          substitute(module("org.apache.iceberg:iceberg-spark3"))
            .using(project(":iceberg-spark:iceberg-spark-3.1_2.12"))
          substitute(module("org.apache.iceberg:iceberg-spark3-extensions"))
            .using(project(":iceberg-spark:iceberg-spark-extensions-3.1_2.12"))
        }
      }
      listOf("1.15").forEach { flinkVersion ->
        substitute(module("org.apache.iceberg:iceberg-flink-$flinkVersion"))
          .using(project(":iceberg-flink:iceberg-flink-$flinkVersion"))
        substitute(module("org.apache.iceberg:iceberg-flink-runtime-$flinkVersion"))
          .using(project(":iceberg-flink:iceberg-flink-runtime-$flinkVersion"))
      }

      val substitutions = this
      all {
        if (!manageNessieProjectDependency("Iceberg", substitutions)) {
          val req = requested
          if (req is ModuleComponentSelector && req.version.isEmpty()) {
            var ver = icebergVersions["${req.group}:${req.module}"]
            if (ver == null) {
              ver = icebergVersions["${req.group}:*"]
            }
            if (ver != null) {
              logger.info(
                "Nessie/Iceberg - managed {}:{} with version {}",
                req.group,
                req.module,
                ver
              )
              useTarget(module("${req.group}:${req.module}:${ver}"), "Managed via Nessie")
            }
          }
        }
      }
    }
  }
}

if (includeNessieBuild) {
  logger.lifecycle("Including 'Nessie-Iceberg' from $nessieSourceDir/nessie-iceberg")
  includeBuild("$nessieSourceDir/nessie-iceberg") {
    name = "nessie-iceberg"

    dependencySubstitution {
      val substitutions = this
      all { manageNessieProjectDependency("Nessie-Iceberg", substitutions) }
    }
  }
}

include("nqeit-nessie-common")

include("nqeit-spark-common")

include("nqeit-iceberg-spark-3.1")

include("nqeit-iceberg-spark-3.2")

include("nqeit-iceberg-flink-common")

include("nqeit-iceberg-flink-1.14")

include("nqeit-iceberg-flink-1.15")

include("nqeit-presto-common")

include("nqeit-presto-current")

// include("trino")

// include("presto")

if (false) {
  include("gradle:dependabot")
}
