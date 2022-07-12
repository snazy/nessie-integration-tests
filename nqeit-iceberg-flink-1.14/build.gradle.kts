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

dependencies {
  compileOnly(platform(rootProject))
  testCompileOnly(platform(rootProject))

  implementation("org.apache.iceberg:iceberg-core")
  implementation("org.apache.iceberg:iceberg-api")

  val flinkVersion = "1.14.5"
  val scalaVersion = "2.12"
  testRuntimeOnly("org.apache.flink:flink-connector-test-utils:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-core:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-runtime:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-streaming-java_$scalaVersion:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-table-planner_$scalaVersion:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-table-api-java-bridge_$scalaVersion:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-connector-base:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-connector-files:$flinkVersion")
  testRuntimeOnly("org.apache.flink:flink-test-utils-junit:$flinkVersion") { exclude("junit") }
  testRuntimeOnly("org.apache.flink:flink-test-utils_2.12:$flinkVersion")

  val hadoopVersion = "3.3.3"
  testRuntimeOnly("org.apache.hadoop:hadoop-common:$hadoopVersion")
  testRuntimeOnly("org.apache.hadoop:hadoop-hdfs:$hadoopVersion")

  testImplementation(project(":nqeit-nessie-common"))
  testImplementation(project(":nqeit-iceberg-flink-common"))
  testImplementation("org.apache.iceberg", "iceberg-flink-runtime-1.15", configuration = "shadow")

  compileOnly("org.eclipse.microprofile.openapi:microprofile-openapi-api")

  testImplementation("org.assertj:assertj-core")
  testImplementation(platform("org.junit:junit-bom"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

  testRuntimeOnly("ch.qos.logback:logback-classic")
  testRuntimeOnly("org.slf4j:log4j-over-slf4j")
}

// Note: Nessie-Quarkus server dependency and Projectnessie plugin are automatically configured,
// when the Projectnessie plugin's included in the `plugins` section.
