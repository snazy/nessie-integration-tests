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

val scalaVersion = dependencyVersion("versionScala2_12")

val sparkVersion = dependencyVersion("versionSpark32")

dependencies {
  // picks the right dependencies for scala compilation
  forScala(scalaVersion)

  compileOnly(platform(rootProject))

  compileOnly("org.apache.spark:spark-sql_2.12") { forSpark(sparkVersion) }
  compileOnly("org.apache.spark:spark-core_2.12") { forSpark(sparkVersion) }
  compileOnly("org.apache.spark:spark-hive_2.12") { forSpark(sparkVersion) }

  compileOnly("org.eclipse.microprofile.openapi:microprofile-openapi-api")

  implementation("org.projectnessie:nessie-client")

  implementation("org.assertj:assertj-core")
  implementation(platform("org.junit:junit-bom"))
  implementation("org.junit.jupiter:junit-jupiter-api")
  implementation("org.junit.jupiter:junit-jupiter-params")
}