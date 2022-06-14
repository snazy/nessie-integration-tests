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
package org.projectnessie.integtests.nessie.internal;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;

public class DefaultBranchPerRun {

  private final Branch defaultBranch;

  public Branch getDefaultBranch() {
    return defaultBranch;
  }

  public static DefaultBranchPerRun get(ExtensionContext extensionContext) {
    return extensionContext
        .getRoot()
        .getStore(Util.NAMESPACE)
        .getOrComputeIfAbsent(
            DefaultBranchPerRun.class,
            x -> new DefaultBranchPerRun(extensionContext),
            DefaultBranchPerRun.class);
  }

  private DefaultBranchPerRun(ExtensionContext extensionContext) {
    extensionContext = extensionContext.getRoot();

    NessieEnv env = NessieEnv.get(extensionContext);

    String dateTime =
        new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral('-')
            .appendValue(MINUTE_OF_HOUR, 2)
            .toFormatter(Locale.getDefault())
            .format(LocalDateTime.now());

    String testBranchName =
        ReferencesHelper.get(extensionContext)
            .generateRefNameFor("default-" + dateTime, extensionContext);

    Branch initialDefaultBranch = env.getInitialDefaultBranch();

    try {
      defaultBranch =
          (Branch)
              env.getApi()
                  .createReference()
                  .reference(Branch.of(testBranchName, initialDefaultBranch.getHash()))
                  .sourceRefName(initialDefaultBranch.getName())
                  .create();
    } catch (NessieNotFoundException | NessieConflictException e) {
      throw new RuntimeException(e);
    }
  }
}
