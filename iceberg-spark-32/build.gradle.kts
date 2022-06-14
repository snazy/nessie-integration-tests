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

val scalaVersion = dependencyVersion("versionScala2_12")

val sparkVersion = dependencyVersion("versionSpark32")

dependencies {
  // picks the right dependencies for scala compilation
  forScala(scalaVersion)

  compileOnly(platform(rootProject))
  testCompileOnly(platform(rootProject))

  implementation("org.apache.iceberg:iceberg-api")

  testImplementation(project(":iceberg-common"))

  testImplementation("org.projectnessie:nessie-spark-3.2-extensions")

  compileOnly("org.apache.spark:spark-sql_2.12") { forSpark(sparkVersion) }
  compileOnly("org.apache.spark:spark-core_2.12") { forSpark(sparkVersion) }
  compileOnly("org.apache.spark:spark-hive_2.12") { forSpark(sparkVersion) }

  compileOnly("org.eclipse.microprofile.openapi:microprofile-openapi-api")

  testImplementation("org.apache.spark:spark-sql_2.12") { forSpark(sparkVersion) }
  testImplementation("org.apache.spark:spark-core_2.12") { forSpark(sparkVersion) }
  testImplementation("org.apache.spark:spark-hive_2.12") { forSpark(sparkVersion) }

  testImplementation("org.assertj:assertj-core")
  testImplementation(platform("org.junit:junit-bom"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

  testRuntimeOnly("ch.qos.logback:logback-classic")
  testRuntimeOnly("org.slf4j:log4j-over-slf4j")

  nessieQuarkusServer("org.projectnessie", "nessie-quarkus", configuration = "quarkusRunner")
}

nessieQuarkusApp {
  includeTask(tasks.named<Test>("intTest"))
  environmentNonInput.put("HTTP_ACCESS_LOG_LEVEL", testLogLevel())
}

tasks.withType<Test>().configureEach { systemProperty("spark.master.url", "local[2]") }

forceJava11ForTests()
