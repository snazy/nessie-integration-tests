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
package org.projectnessie.integtests.iceberg.spark;

import org.junit.jupiter.api.Test;

public class ITSparkExtensions extends Configs {

  @Test
  public void createBranch() {
    String branchName = generateReferenceName("dev");
    System.err.println(sql("CREATE BRANCH %s IN nessie FROM main", branchName).collectAsList());
    System.err.println(sql("LIST REFERENCES IN nessie").collectAsList());
  }

  @Test
  public void listReferences() {
    System.err.println(sql("LIST REFERENCES IN nessie").collectAsList());
  }
}
