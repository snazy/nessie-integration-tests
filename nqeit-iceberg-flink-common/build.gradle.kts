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
}

dependencies {
  implementation(platform(rootProject))
  implementation(project(":nqeit-nessie-common"))
  implementation("com.google.code.findbugs:jsr305")

  compileOnly("org.apache.iceberg:iceberg-core")
  compileOnly("org.apache.iceberg:iceberg-flink-1.15")

  val flinkVersion = "1.15.1"
  val scalaVersion = "2.12"
  compileOnly("org.apache.flink:flink-connector-test-utils:$flinkVersion")
  compileOnly("org.apache.flink:flink-core:$flinkVersion")
  compileOnly("org.apache.flink:flink-runtime:$flinkVersion")
  compileOnly("org.apache.flink:flink-streaming-java:$flinkVersion")
  compileOnly("org.apache.flink:flink-table-planner_$scalaVersion:$flinkVersion")
  compileOnly("org.apache.flink:flink-table-api-java-bridge:$flinkVersion")
  compileOnly("org.apache.flink:flink-connector-base:$flinkVersion")
  compileOnly("org.apache.flink:flink-connector-files:$flinkVersion")
  compileOnly("org.apache.flink:flink-test-utils-junit:$flinkVersion") { exclude("junit") }
  compileOnly("org.apache.flink:flink-test-utils:$flinkVersion")

  implementation("org.assertj:assertj-core")
  implementation(platform("org.junit:junit-bom"))
  implementation("org.junit.jupiter:junit-jupiter-api")
  implementation("org.junit.jupiter:junit-jupiter-params")
}
