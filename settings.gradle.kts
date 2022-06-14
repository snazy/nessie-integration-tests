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
          if (pluginId.startsWith("org.projectnessie.buildsupport.") ||
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
fun projectCanonicalPath(sub: String) {
  val property = "nessie.integrationsTesting.${sub}SourceTree"
  val path =
    rootProject.projectDir
      .resolve(System.getProperty(property, "./included-builds/$sub"))
      .canonicalPath
  System.setProperty(property, path)
  logger.lifecycle("Using '$property' from $path")
}

listOf("nessie", "iceberg").forEach { sub -> projectCanonicalPath(sub) }

val nessieDir =
  System.getProperty("nessie.integrationsTesting.nessieSourceTree", "./included-builds/nessie")

file("$nessieDir/build").mkdirs()

file("$nessieDir/build/nessie-integrations.properties").bufferedWriter().use { w ->
  System.getProperties()
    .filter { e -> e.key.toString().startsWith("nessie.") }
    .forEach { e -> w.write("${e.key}=${e.value}\n") }
}

includeBuild(nessieDir) {
  name = "nessie"
  dependencySubstitution {
    substitute(module("org.projectnessie:nessie-model")).using(project(":model"))

    substitute(module("org.projectnessie:nessie-jaxrs-testextension"))
      .using(project(":servers:jax-rs-testextension"))

    substitute(module("org.projectnessie:nessie-client")).using(project(":clients:client"))
    substitute(module("org.projectnessie:nessie-deltalake")).using(project(":clients:deltalake"))
    substitute(module("org.projectnessie:iceberg-views")).using(project(":clients:iceberg-views"))
    substitute(module("org.projectnessie:nessie-spark-extensions"))
      .using(project(":clients:spark-31-extensions"))
    substitute(module("org.projectnessie:nessie-spark-3.2-extensions"))
      .using(project(":clients:spark-32-extensions"))

    substitute(module("org.projectnessie:nessie-quarkus")).using(project(":servers:quarkus-server"))
  }
}

include("iceberg-common")

include("iceberg-spark-31")

include("iceberg-spark-32")

// include("trino")

// include("presto")

if (false) {
  include("gradle:dependabot")
}
