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
package org.projectnessie.integtests.crossengine;

import static java.lang.String.format;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.integtests.flink.Flink;
import org.projectnessie.integtests.flink.FlinkHelper;
import org.projectnessie.integtests.flink.IcebergFlinkExtension;
import org.projectnessie.integtests.iceberg.spark.IcebergSparkExtension;
import org.projectnessie.integtests.iceberg.spark.Spark;
import org.projectnessie.integtests.nessie.NessieTestsExtension;
import org.projectnessie.integtests.presto.PrestoJdbc;
import org.projectnessie.integtests.presto.PrestoJdbcExtension;

@ExtendWith({
  IcebergSparkExtension.class,
  IcebergFlinkExtension.class,
  PrestoJdbcExtension.class,
  NessieTestsExtension.class
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ITCrossEngine {

  @Order(100)
  @Test
  public void createTables(
      @PrestoJdbc Connection presto, @Spark SparkSession spark, @Flink FlinkHelper flink)
      throws Exception {
    spark.sql("CREATE TABLE nessie.db.from_spark (id int, val string)");

    flink.sql("CREATE TABLE %s (id int, val string)", flink.qualifiedTableName("from_flink"));

    try (Statement s = presto.createStatement()) {
      s.execute("CREATE TABLE db.from_presto (id INT, val VARCHAR)");
    }
  }

  static List<String> tables() {
    return Arrays.asList("from_spark", "from_flink", "from_presto");
  }

  @Order(101)
  @ParameterizedTest
  @MethodSource("tables")
  public void insertIntoTables(
      String table,
      @PrestoJdbc Connection presto,
      @Spark SparkSession spark,
      @Flink FlinkHelper flink)
      throws Exception {
    spark.sql(format("INSERT INTO nessie.db.%s select 100, \"from-spark\"", table));

    flink.sql(
        "INSERT INTO %s (id, val) VALUES (200, 'from-flink')", flink.qualifiedTableName(table));

    try (Statement s = presto.createStatement()) {
      s.execute(format("INSERT INTO db.%s (id, val) VALUES (300, 'from-presto')", table));
    }
  }

  @Order(102)
  @ParameterizedTest
  @MethodSource("tables")
  public void selectFromTables(
      String table,
      @PrestoJdbc Connection presto,
      @Spark SparkSession spark,
      @Flink FlinkHelper flink)
      throws Exception {
    spark.sql(format("SELECT id, val FROM nessie.db.%s", table));

    //    flink.sql("SELECT id, val FROM %s", flink.qualifiedTableName(table));

    try (Statement s = presto.createStatement()) {
      try (ResultSet rs = s.executeQuery(format("SELECT id, val FROM db.%s", table))) {
        //        while (rs.next()) {
        //          rs.getInt(1);
        //          rs.getString(2);
        //        }
      }
    }
  }

  @Order(103)
  @ParameterizedTest
  @MethodSource("tables")
  public void insertIntoTables2(
      String table,
      @PrestoJdbc Connection presto,
      @Spark SparkSession spark,
      @Flink FlinkHelper flink)
      throws Exception {
    spark.sql(format("INSERT INTO nessie.db.%s select 101, \"from-spark\"", table));

    flink.sql(
        "INSERT INTO %s (id, val) VALUES (201, 'from-flink')", flink.qualifiedTableName(table));

    try (Statement s = presto.createStatement()) {
      s.execute(format("INSERT INTO db.%s (id, val) VALUES (301, 'from-presto')", table));
    }
  }

  @Order(104)
  @ParameterizedTest
  @MethodSource("tables")
  public void selectFromTables2(
      String table,
      @PrestoJdbc Connection presto,
      @Spark SparkSession spark,
      @Flink FlinkHelper flink)
      throws Exception {
    spark.sql(format("SELECT id, val FROM nessie.db.%s", table));

    //    flink.sql("SELECT id, val FROM %s", flink.qualifiedTableName(table));

    try (Statement s = presto.createStatement()) {
      try (ResultSet rs = s.executeQuery(format("SELECT id, val FROM db.%s", table))) {
        //        while (rs.next()) {
        //          rs.getInt(1);
        //          rs.getString(2);
        //        }
      }
    }
  }
}
