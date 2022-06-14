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
package org.projectnessie.integtests.nessie;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.client.http.HttpClientBuilder;
import org.projectnessie.model.Branch;
import org.projectnessie.model.Reference;
import org.projectnessie.model.Tag;

/**
 * Implementation of {@link NessieTestApi} for Nessie related tests, to be extended by per-test
 * project. This implementation hides the JUnit machinery from the Test-API interface.
 */
public abstract class NessieTestConfigs implements NessieTestApi {

  private static final int NESSIE_PORT = Integer.getInteger("quarkus.http.test-port", 19121);
  private static final String NESSIE_URI =
      System.getProperty(
          "quarkus.http.url", String.format("http://localhost:%d/api/v1", NESSIE_PORT));

  private static NessieApiV1 nessieApi;
  private static Branch initialDefaultBranch;
  private static long startedNanos;

  @BeforeAll
  static void setupApi() throws Exception {
    nessieApi = HttpClientBuilder.builder().withUri(NESSIE_URI).build(NessieApiV1.class);
    initialDefaultBranch = nessieApi.getDefaultBranch();
    startedNanos = System.nanoTime() % 1_000_000;
  }

  @AfterAll
  static void closeApi() {
    NessieApiV1 api = nessieApi;
    nessieApi = null;
    if (api != null) {
      api.close();
    }
  }

  private String referenceNamePrefix;
  private String testBranchName;
  private final Set<String> generatedReferenceNames = new HashSet<>();

  @BeforeEach
  void beforeTestCase(TestInfo testInfo) throws Exception {
    referenceNamePrefix =
        String.format(
            "%s_%s_%s_",
            testInfo.getTestClass().get().getSimpleName().toLowerCase(Locale.ROOT),
            startedNanos,
            testInfo.getTestMethod().get().getName().toLowerCase(Locale.ROOT));
    generatedReferenceNames.clear();

    testBranchName = generateReferenceName("default");

    api()
        .createReference()
        .reference(Branch.of(testBranchName, initialDefaultBranch.getHash()))
        .sourceRefName(initialDefaultBranch.getName())
        .create();
  }

  @AfterEach
  void resetNessie() throws Exception {
    if (Boolean.getBoolean("nessie.test.keepReferences")) {
      return;
    }
    for (Reference ref : api().getAllReferences().get().getReferences()) {
      if (ref instanceof Branch && generatedReferenceNames.remove(ref.getName())) {
        api().deleteBranch().branchName(ref.getName()).hash(ref.getHash()).delete();
      }
      if (ref instanceof Tag && generatedReferenceNames.remove(ref.getName())) {
        api().deleteTag().tagName(ref.getName()).hash(ref.getHash()).delete();
      }
    }
  }

  @Nonnull
  @Override
  public String generateReferenceName(@Nonnull String name) {
    String refName =
        String.format("%s_%s%d", name, referenceNamePrefix, generatedReferenceNames.size());
    generatedReferenceNames.add(refName);
    return refName;
  }

  @Override
  @Nonnull
  public String nessieUri() {
    return NESSIE_URI;
  }

  public static String nessieUriStatic() {
    return NESSIE_URI;
  }

  @Override
  public void consumeNessieProperties(BiConsumer<String, String> keyValueConsumer) {
    keyValueConsumer.accept("ref", testBranchName);
    keyValueConsumer.accept("uri", nessieUri());
    System.getProperties().entrySet().stream()
        .filter(e -> e.getKey().toString().startsWith("nessie.client."))
        .forEach(
            e ->
                keyValueConsumer.accept(
                    e.getKey().toString().substring("nessie.client.".length()),
                    e.getValue().toString()));
  }

  @Override
  @Nonnull
  public NessieApiV1 api() {
    return nessieApi;
  }
}
