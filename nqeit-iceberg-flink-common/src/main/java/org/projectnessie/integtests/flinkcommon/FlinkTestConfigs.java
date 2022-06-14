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
package org.projectnessie.integtests.flinkcommon;

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.FormatMethod;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.apache.iceberg.flink.FlinkConfigOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.projectnessie.integtests.nessie.NessieTestConfigs;

/**
 * Default implementation for Flink related tests, to be extended by per-test project. This
 * implementation hides the JUnit machinery from the Test-API interface.
 */
public abstract class FlinkTestConfigs extends NessieTestConfigs implements FlinkTestApi {

  private static final int DEFAULT_TM_NUM = 1;
  private static final int DEFAULT_PARALLELISM = 4;
  private static final Configuration DISABLE_CLASSLOADER_CHECK_CONFIG =
      new Configuration()
          // disable classloader check as Avro may cache class/object in the serializers.
          .set(CoreOptions.CHECK_LEAKED_CLASSLOADER, false);

  private static MiniClusterWithClientResource flinkMiniCluster;
  private static TableEnvironment tableEnvironment;
  private static String catalogName;

  @BeforeAll
  static void startFlink() {
    flinkMiniCluster = createWithClassloaderCheckDisabled();

    EnvironmentSettings settings = EnvironmentSettings.newInstance().inBatchMode().build();

    TableEnvironment env = TableEnvironment.create(settings);
    env.getConfig()
        .getConfiguration()
        .set(FlinkConfigOptions.TABLE_EXEC_ICEBERG_INFER_SOURCE_PARALLELISM, false);
    tableEnvironment = env;
  }

  @AfterAll
  static void stopFlink() {
    flinkMiniCluster.after();
  }

  @BeforeEach
  void setupFlinkConf(@TempDir File tempDir) throws Exception {
    catalogName = getClass().getSimpleName().toLowerCase(Locale.ROOT);
    flinkMiniCluster.before();

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
      flinkMiniCluster.after();
    }
  }

  @Override
  public String catalogName() {
    return catalogName;
  }

  @Override
  public TableEnvironment getTableEnv() {
    return tableEnvironment;
  }

  @Override
  @FormatMethod
  public TableResult exec(TableEnvironment env, String query, Object... args) {
    return env.executeSql(String.format(query, args));
  }

  @Override
  @FormatMethod
  public TableResult exec(String query, Object... args) {
    return exec(getTableEnv(), query, args);
  }

  @Override
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
