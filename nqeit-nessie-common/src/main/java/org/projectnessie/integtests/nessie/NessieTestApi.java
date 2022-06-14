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

import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.projectnessie.client.api.NessieApiV1;

/** API for Nessie related tests. */
public interface NessieTestApi {

  @Nonnull
  String nessieUri();

  /**
   * Pushes all Nessie configuration options like the Nessie API endpoint URI and default branch
   * name to the provided consumer.
   */
  void consumeNessieProperties(BiConsumer<String, String> keyValueConsumer);

  /**
   * Generates a (very likely) unique reference name.
   *
   * <p>Do not use "constant" names hard coded in test source code, so that concurrent tests against
   * a <em>shared</em> Nessie server instance do not interfere with each other.
   */
  @Nonnull
  String generateReferenceName(@Nonnull String name);

  @Nonnull
  NessieApiV1 api();
}
