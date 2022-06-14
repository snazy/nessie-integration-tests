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
package org.projectnessie.integtests.presto.current;

import static java.util.Locale.ENGLISH;

import com.facebook.presto.Session;
import com.facebook.presto.common.type.TimeZoneKey;
import com.facebook.presto.execution.QueryIdGenerator;
import com.facebook.presto.iceberg.CatalogType;
import com.facebook.presto.iceberg.IcebergConfig;
import com.facebook.presto.iceberg.IcebergPlugin;
import com.facebook.presto.metadata.SessionPropertyManager;
import com.facebook.presto.spi.security.Identity;
import com.facebook.presto.testing.MaterializedResult;
import com.facebook.presto.tests.DistributedQueryRunner;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.FormatMethod;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.iceberg.FileFormat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.projectnessie.integtests.nessie.NessieTestConfigs;

public abstract class Configs extends NessieTestConfigs {

  public static final String ICEBERG_CATALOG = "iceberg";

  public static final TimeZoneKey DEFAULT_TIME_ZONE_KEY = TimeZoneKey.UTC_KEY;

  private static QueryIdGenerator queryIdGenerator;

  private static DistributedQueryRunner queryRunner;
  private static Session session;

  @BeforeAll
  static void setup(@TempDir Path baseDataDir) throws Exception {
    queryIdGenerator = new QueryIdGenerator();

    SessionPropertyManager sessionPropertyManager = new SessionPropertyManager();

    Session.SessionBuilder sessionBuilder =
        Session.builder(sessionPropertyManager)
            .setQueryId(queryIdGenerator.createNextQueryId())
            .setIdentity(new Identity("user", Optional.empty()))
            .setSource("test")
            .setSchema("schema")
            .setTimeZoneKey(DEFAULT_TIME_ZONE_KEY)
            .setLocale(ENGLISH)
            .setRemoteUserAddress("address")
            .setUserAgent("agent")
            .setCatalog(ICEBERG_CATALOG);

    session = sessionBuilder.build();

    Map<String, String> extraProperties = ImmutableMap.of();
    Map<String, String> extraConnectorProperties = new HashMap<>();
    extraConnectorProperties.put("iceberg.catalog.type", CatalogType.NESSIE.name());
    extraConnectorProperties.put("iceberg.nessie.uri", nessieUriStatic());

    FileFormat format = new IcebergConfig().getFileFormat();

    queryRunner =
        DistributedQueryRunner.builder(session)
            .setBaseDataDir(Optional.of(baseDataDir))
            .setExtraProperties(extraProperties)
            .build();

    Path dataDir = queryRunner.getCoordinator().getBaseDataDir().resolve("iceberg_data");
    Path catalogDir = dataDir.getParent().resolve("catalog");

    queryRunner.installPlugin(new IcebergPlugin());
    Map<String, String> icebergProperties =
        ImmutableMap.<String, String>builder()
            .put("hive.metastore", "file")
            .put("hive.metastore.catalog.dir", catalogDir.toFile().toURI().toString())
            .put("iceberg.file-format", format.name())
            .put("iceberg.catalog.warehouse", dataDir.getParent().toFile().toURI().toString())
            .putAll(extraConnectorProperties)
            .build();

    queryRunner.createCatalog(ICEBERG_CATALOG, "iceberg", icebergProperties);
  }

  @AfterAll
  static void tearDown() {
    session = null;
    if (queryRunner != null) {
      try {
        queryRunner.close();
      } finally {
        queryRunner = null;
      }
    }
  }

  @FormatMethod
  public MaterializedResult sql(String sql, Object... args) {
    return queryRunner.execute(session, String.format(sql, args));
  }

  @FormatMethod
  public Object sqlScalar(String sql, Object... args) {
    return sql(sql, args).getOnlyValue();
  }
}
