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
package org.projectnessie.integtests.flink;

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.FormatMethod;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CoreOptions;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.table.api.EnvironmentSettings;
import org.apache.flink.table.api.TableEnvironment;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.test.util.MiniClusterWithClientResource;
import org.apache.flink.types.Row;
import org.apache.flink.util.CloseableIterator;
import org.apache.iceberg.CatalogProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.projectnessie.integtests.nessie.NessieTestConfigs;

/**
 * Provides machinery for tests in this project to access Nessie + Spark w/ Nessie Spark Extensions.
 */
public abstract class Configs extends NessieTestConfigs {

  private static final int DEFAULT_TM_NUM = 1;
  private static final int DEFAULT_PARALLELISM = 4;
  private static final Configuration DISABLE_CLASSLOADER_CHECK_CONFIG =
      new Configuration()
          // disable classloader check as Avro may cache class/object in the serializers.
          .set(CoreOptions.CHECK_LEAKED_CLASSLOADER, false);
  public static final String FLINK_CONFIG_PREFIX = "flink.config.";

  private static MiniClusterWithClientResource flinkMiniCluster;
  private static TableEnvironment tableEnvironment;
  private String catalogName;
  private String databaseName = "default";

  @BeforeAll
  static void startFlink() {
    EnvironmentSettings settings = EnvironmentSettings.newInstance().inBatchMode().build();

    TableEnvironment env = TableEnvironment.create(settings);
    Configuration configuration = env.getConfig().getConfiguration();

    if (System.getProperty("flink.remote.host") != null) {
      System.getProperties().entrySet().stream()
          .filter(e -> e.getKey().toString().startsWith(FLINK_CONFIG_PREFIX))
          .forEach(
              e ->
                  configuration.setString(
                      e.getKey().toString().substring(FLINK_CONFIG_PREFIX.length()),
                      e.getValue().toString()));

      String flinkHost = System.getProperty("flink.remote.host");
      int flinkPort = Integer.getInteger("flink.remote.port", 8081);

      ExecutionEnvironment executionEnvironment =
          ExecutionEnvironment.createRemoteEnvironment(flinkHost, flinkPort);
      env.getConfig().addConfiguration(executionEnvironment.getConfiguration());
    } else {
      flinkMiniCluster = createWithClassloaderCheckDisabled();
    }

    tableEnvironment = env;
  }

  @AfterAll
  static void stopFlink() {
    if (flinkMiniCluster != null) {
      flinkMiniCluster.after();
    }
  }

  @BeforeEach
  void setupFlinkConf(TestInfo testInfo, @TempDir File tempDir) throws Exception {
    catalogName =
        String.format(
            "%s_%s",
            testInfo.getTestClass().get().getSimpleName().toLowerCase(Locale.ROOT),
            testInfo.getTestMethod().get().getName().toLowerCase(Locale.ROOT));

    if (flinkMiniCluster != null) {
      flinkMiniCluster.before();
    }

    Map<String, String> config = new HashMap<>();
    config.put("type", "iceberg");
    config.put(CatalogProperties.CATALOG_IMPL, "org.apache.iceberg.nessie.NessieCatalog");
    consumeNessieProperties(config::put);
    config.put(
        CatalogProperties.WAREHOUSE_LOCATION,
        String.format("file://%s", tempDir.getAbsolutePath()));

    sql("CREATE CATALOG %s WITH %s", catalogName(), toWithClause(config));
  }

  @AfterEach
  void cleanupAfterTest() {
    try {
      sql("DROP CATALOG IF EXISTS %s", catalogName());
    } finally {
      if (flinkMiniCluster != null) {
        flinkMiniCluster.after();
      }
    }
  }

  public String catalogName() {
    return catalogName;
  }

  public String databaseName() {
    return databaseName;
  }

  public String qualifiedTableName(String tableName) {
    return String.format("`%s`.`%s`.`%s`", catalogName(), databaseName(), tableName);
  }

  public TableEnvironment getTableEnv() {
    return tableEnvironment;
  }

  @FormatMethod
  public TableResult exec(TableEnvironment env, String query, Object... args) {
    return env.executeSql(String.format(query, args));
  }

  @FormatMethod
  public TableResult exec(String query, Object... args) {
    return exec(getTableEnv(), query, args);
  }

  @FormatMethod
  public List<Row> sql(String query, Object... args) {
    TableResult tableResult = exec(query, args);
    try (CloseableIterator<Row> iter = tableResult.collect()) {
      return Lists.newArrayList(iter);
    } catch (Exception e) {
      throw new RuntimeException("Failed to collect table result", e);
    }
  }

  /**
   * It will start a mini cluster with classloader.check-leaked-classloader=false, so that we won't
   * break the unit tests because of the class loader leak issue. In our iceberg integration tests,
   * there're some that will assert the results after finished the flink jobs, so actually we may
   * access the class loader that has been closed by the flink task managers if we enable the switch
   * classloader.check-leaked-classloader by default.
   */
  static MiniClusterWithClientResource createWithClassloaderCheckDisabled() {
    return new MiniClusterWithClientResource(
        new MiniClusterResourceConfiguration.Builder()
            .setNumberTaskManagers(DEFAULT_TM_NUM)
            .setNumberSlotsPerTaskManager(DEFAULT_PARALLELISM)
            .setConfiguration(DISABLE_CLASSLOADER_CHECK_CONFIG)
            .build());
  }

  static String toWithClause(Map<String, String> props) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    int propCount = 0;
    for (Map.Entry<String, String> entry : props.entrySet()) {
      if (propCount > 0) {
        builder.append(",");
      }
      builder
          .append("'")
          .append(entry.getKey())
          .append("'")
          .append("=")
          .append("'")
          .append(entry.getValue())
          .append("'");
      propCount++;
    }
    builder.append(")");
    return builder.toString();
  }
}
