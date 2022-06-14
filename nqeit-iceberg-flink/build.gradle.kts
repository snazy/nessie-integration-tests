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

plugins {
  `java-library`
  `tools-integrations-conventions`
  id("org.projectnessie")
}

val flink = getFlinkVersionsForProject()

dependencies {
  compileOnly(platform(rootProject))
  testCompileOnly(platform(rootProject))

  implementation(project(":nqeit-nessie-common"))
  implementation("com.google.code.findbugs:jsr305")

  implementation("org.apache.iceberg:iceberg-core")
  implementation("org.apache.iceberg:iceberg-api")

  testImplementation(project(":nqeit-nessie-common"))
  testImplementation("org.apache.iceberg", "iceberg-flink-${flink.flinkMajorVersion}")
  testImplementation("org.apache.iceberg", "iceberg-nessie")

  testImplementation("org.apache.flink:flink-connector-test-utils:${flink.flinkVersion}")
  testImplementation("org.apache.flink:flink-core:${flink.flinkVersion}")
  testImplementation("org.apache.flink:flink-runtime:${flink.flinkVersion}")
  testImplementation(
    "org.apache.flink:flink-streaming-java${flink.scalaForDependencies}:${flink.flinkVersion}"
  )
  testImplementation(
    "org.apache.flink:flink-table-planner_${flink.scalaMajorVersion}:${flink.flinkVersion}"
  )
  testImplementation("org.apache.flink:flink-table-api-java:${flink.flinkVersion}")
  testImplementation(
    "org.apache.flink:flink-table-api-java-bridge${flink.scalaForDependencies}:${flink.flinkVersion}"
  )
  testImplementation("org.apache.flink:flink-connector-base:${flink.flinkVersion}")
  testImplementation("org.apache.flink:flink-connector-files:${flink.flinkVersion}")
  testImplementation("org.apache.flink:flink-test-utils-junit:${flink.flinkVersion}") {
    exclude("junit")
  }
  testImplementation(
    "org.apache.flink:flink-test-utils${flink.scalaForDependencies}:${flink.flinkVersion}"
  )

  testRuntimeOnly("org.apache.hadoop:hadoop-common:${flink.hadoopVersion}")
  testRuntimeOnly("org.apache.hadoop:hadoop-hdfs:${flink.hadoopVersion}")

  compileOnly("org.eclipse.microprofile.openapi:microprofile-openapi-api")

  testImplementation("org.assertj:assertj-core")
  testImplementation(platform("org.junit:junit-bom"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

  testRuntimeOnly("ch.qos.logback:logback-classic")
  testRuntimeOnly("org.slf4j:log4j-over-slf4j")
}

tasks.withType<Test>().configureEach {
  systemProperty("flink.flinkMajorVersion", flink.flinkMajorVersion)
  systemProperty("flink.flinkVersion", flink.flinkVersion)
  systemProperty("flink.hadoopVersion", flink.hadoopVersion)
  systemProperty("flink.scalaMajorVersion", flink.scalaMajorVersion)
}

// Note: Nessie-Quarkus server dependency and Projectnessie plugin are automatically configured,
// when the Projectnessie plugin's included in the `plugins` section.
