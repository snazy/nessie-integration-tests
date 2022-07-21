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

val presto = getPrestoVersionsForProject()

dependencies {
  implementation(platform(rootProject))
  implementation(project(":nqeit-nessie-common"))
  implementation("com.google.code.findbugs:jsr305")

  testImplementation("com.facebook.presto:presto-main:${presto.prestoVersion}")
  testImplementation("com.facebook.presto:presto-tests:${presto.prestoVersion}")
  testImplementation("com.facebook.presto:presto-iceberg:${presto.prestoVersion}")

  testImplementation("org.assertj:assertj-core")
  testImplementation(platform("org.junit:junit-bom"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

  testRuntimeOnly("ch.qos.logback:logback-classic")
  testRuntimeOnly("org.slf4j:log4j-over-slf4j")
}
