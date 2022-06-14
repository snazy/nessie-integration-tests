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
package org.projectnessie.integtests.presto;

import static org.projectnessie.integtests.nessie.internal.Util.checkSupportedParameterType;

import com.facebook.presto.iceberg.CatalogType;
import com.facebook.presto.iceberg.IcebergConfig;
import com.facebook.presto.iceberg.IcebergPlugin;
import com.facebook.presto.jdbc.PrestoDriver;
import com.facebook.presto.server.testing.TestingPrestoServer;
import com.facebook.presto.sql.parser.SqlParserOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.iceberg.FileFormat;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.projectnessie.integtests.nessie.internal.DefaultBranchPerRun;
import org.projectnessie.integtests.nessie.internal.HiveMetastoreCatalog;
import org.projectnessie.integtests.nessie.internal.IcebergWarehouse;
import org.projectnessie.integtests.nessie.internal.NessieEnv;
import org.projectnessie.integtests.nessie.internal.PrestoLocalData;
import org.projectnessie.model.Branch;

public class PrestoJdbcExtension implements ParameterResolver {

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(PrestoJdbcExtension.class);

  public static final String ICEBERG_CATALOG = "iceberg";
  public static final String PRESTO_JDBC_HOST_PORT = "presto.jdbc.host-port";
  public static final String PRESTO_SESSION_PREFIX = "presto.session.";

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    if (parameterContext.isAnnotated(PrestoJdbc.class)) {
      checkSupportedParameterType(PrestoJdbc.class, parameterContext, Connection.class);
      return true;
    }
    return false;
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext, ExtensionContext extensionContext)
      throws ParameterResolutionException {
    PrestoJdbc prestoJdbc = parameterContext.findAnnotation(PrestoJdbc.class).get();
    PrestoServer prestoServer = PrestoServer.get(extensionContext);
    try {
      Properties properties = new Properties();

      System.getProperties().entrySet().stream()
          .filter(e -> e.getKey().toString().startsWith(PRESTO_SESSION_PREFIX))
          .forEach(
              e ->
                  properties.put(
                      e.getKey().toString().substring(PRESTO_SESSION_PREFIX.length()),
                      e.getValue().toString()));

      prestoServer.populateConnectionProperties(properties);

      Branch defaultBranch = DefaultBranchPerRun.get(extensionContext).getDefaultBranch();

      // See com.facebook.presto.iceberg.IcebergSessionProperties for valid session properties
      properties.put(
          "sessionProperties", "iceberg.nessie_reference_name:" + defaultBranch.getName());

      Connection conn =
          PrestoDriverResource.get(extensionContext)
              .connect(
                  prestoServer.getHostPort(),
                  prestoJdbc.catalog().isEmpty() ? ICEBERG_CATALOG : prestoJdbc.catalog(),
                  prestoJdbc.schema(),
                  prestoJdbc.extraUrlParameters(),
                  properties);
      PrestoConnections.get(extensionContext).add(conn);
      extensionContext.getStore(NAMESPACE);
      return conn;
    } catch (SQLException e) {
      throw new ParameterResolutionException("Failed to connect to Presto", e);
    }
  }

  private interface PrestoServer {
    static PrestoServer get(ExtensionContext extensionContext) {
      return extensionContext
          .getRoot()
          .getStore(NAMESPACE)
          .getOrComputeIfAbsent(
              PrestoServer.class, c -> createPrestoServer(extensionContext), PrestoServer.class);
    }

    static PrestoServer createPrestoServer(ExtensionContext extensionContext) {
      String hostPort = System.getProperty(PRESTO_JDBC_HOST_PORT);
      if (hostPort == null) {
        return new LocalPrestoServer(extensionContext);
      }

      return new RemotePrestoServer(hostPort);
    }

    String getHostPort();

    void populateConnectionProperties(Properties properties);
  }

  private static class RemotePrestoServer implements PrestoServer {

    private final String hostPort;

    public RemotePrestoServer(String hostPort) {
      this.hostPort = hostPort;
    }

    @Override
    public String getHostPort() {
      return hostPort;
    }

    @Override
    public void populateConnectionProperties(Properties properties) {}
  }

  private static class LocalPrestoServer implements PrestoServer, CloseableResource {
    private final TestingPrestoServer testingPrestoServer;

    public LocalPrestoServer(ExtensionContext extensionContext) {
      try {
        testingPrestoServer =
            new TestingPrestoServer(
                true,
                ImmutableMap.of(),
                null,
                null,
                new SqlParserOptions(),
                ImmutableList.of(),
                Optional.of(PrestoLocalData.get(extensionContext).getPath()));
        testingPrestoServer.installPlugin(new IcebergPlugin());

        FileFormat format = new IcebergConfig().getFileFormat();

        NessieEnv env = NessieEnv.get(extensionContext);

        Map<String, String> extraConnectorProperties =
            ImmutableMap.<String, String>builder()
                .put("iceberg.catalog.type", CatalogType.NESSIE.name())
                .put("iceberg.nessie.uri", env.getNessieUri())
                .build();

        Map<String, String> icebergProperties =
            ImmutableMap.<String, String>builder()
                .put("hive.metastore", "file")
                .put(
                    "hive.metastore.catalog.dir",
                    HiveMetastoreCatalog.get(extensionContext).getUri().toString())
                .put("iceberg.file-format", format.name())
                .put(
                    "iceberg.catalog.warehouse",
                    IcebergWarehouse.get(extensionContext).getUri().toString())
                .putAll(extraConnectorProperties)
                .build();
        testingPrestoServer.createCatalog(ICEBERG_CATALOG, "iceberg", icebergProperties);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public String getHostPort() {
      return testingPrestoServer.getAddress().getHost()
          + ':'
          + testingPrestoServer.getAddress().getPort();
    }

    @Override
    public void populateConnectionProperties(Properties properties) {
      properties.put("user", "admin");
      // properties.put("password", prestoServer.getPassword());
    }

    @Override
    public void close() throws Throwable {
      testingPrestoServer.close();
    }
  }

  private static class PrestoConnections implements CloseableResource {
    private final List<Connection> connections = new ArrayList<>();

    private PrestoConnections() {}

    static PrestoConnections get(ExtensionContext extensionContext) {
      return extensionContext
          .getStore(NAMESPACE)
          .getOrComputeIfAbsent(
              PrestoConnections.class, c -> new PrestoConnections(), PrestoConnections.class);
    }

    @Override
    public void close() throws Throwable {
      Throwable toThrow = null;
      for (Connection connection : connections) {
        try {
          connection.close();
        } catch (Throwable t) {
          if (toThrow == null) {
            toThrow = t;
          } else {
            toThrow.addSuppressed(t);
          }
        }
      }
      if (toThrow != null) {
        throw toThrow;
      }
    }

    public void add(Connection conn) {
      connections.add(conn);
    }
  }

  private static class PrestoDriverResource implements CloseableResource {
    private final PrestoDriver driver;

    PrestoDriverResource() {
      this.driver = new PrestoDriver();
    }

    static PrestoDriverResource get(ExtensionContext extensionContext) {
      return extensionContext
          .getRoot()
          .getStore(NAMESPACE)
          .getOrComputeIfAbsent(
              PrestoDriverResource.class,
              d -> new PrestoDriverResource(),
              PrestoDriverResource.class);
    }

    @Override
    public void close() {
      driver.close();
    }

    Connection connect(
        String hostPort,
        String catalog,
        String schema,
        String extraUrlParameters,
        Properties properties)
        throws SQLException {
      return driver.connect(
          String.format("jdbc:presto://%s/%s/%s?%s", hostPort, catalog, schema, extraUrlParameters),
          properties);
    }
  }
}
