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
package org.projectnessie.integtests.spark;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.apache.spark.SparkConf;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.internal.SQLConf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.projectnessie.integtests.nessie.NessieTestConfigs;

/**
 * Default implementation for Spark related tests, to be extended by per-test project. This
 * implementation hides the JUnit machinery from the Test-API interface.
 */
public abstract class SparkTestConfigs extends NessieTestConfigs implements SparkTestApi {

  private static final String SPARK_MASTER_URL = System.getProperty("spark.master.url");
  private static final String NON_NESSIE_CATALOG = "invalid_hive";

  private static SparkSession sparkSession;

  @BeforeEach
  void setupSparkConf(@TempDir File tempDir) {
    SparkConf conf = new SparkConf();

    Map<String, String> nessieParams = ImmutableMap.of("warehouse", tempDir.toURI().toString());

    BiConsumer<String, String> confConsumer =
        (k, v) -> {
          conf.set(String.format("spark.sql.catalog.nessie.%s", k), v);
          conf.set(String.format("spark.sql.catalog.spark_catalog.%s", k), v);
        };

    consumeNessieProperties(confConsumer);
    nessieParams.forEach(confConsumer);

    conf.set(SQLConf.PARTITION_OVERWRITE_MODE().key(), "dynamic")
        .set("spark.testing", "true")
        .set("spark.sql.shuffle.partitions", "4")
        .set("spark.sql.catalog.nessie.catalog-impl", "org.apache.iceberg.nessie.NessieCatalog")
        .set("spark.sql.catalog.nessie", "org.apache.iceberg.spark.SparkCatalog");

    updateSparkConf(conf);

    // the following catalog is only added to test a check in the nessie spark extensions
    conf.set(
            String.format("spark.sql.catalog.%s", NON_NESSIE_CATALOG),
            "org.apache.iceberg.spark.SparkCatalog")
        .set(
            String.format("spark.sql.catalog.%s.catalog-impl", NON_NESSIE_CATALOG),
            "org.apache.iceberg.hive.HiveCatalog");

    updateSparkConf(conf);

    SparkSession spark = SparkSession.builder().master(SPARK_MASTER_URL).config(conf).getOrCreate();
    spark.sparkContext().setLogLevel("WARN");
    sparkSession = spark;
  }

  public void updateSparkConf(SparkConf conf) {}

  @AfterAll
  static void stopSpark() {
    SparkSession spark = sparkSession;
    sparkSession = null;
    if (spark != null) {
      spark.stop();
    }
  }

  @Override
  @Nonnull
  public SparkSession spark() {
    return sparkSession;
  }
}
