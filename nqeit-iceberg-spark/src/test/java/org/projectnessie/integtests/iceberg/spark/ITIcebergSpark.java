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

import static java.lang.String.format;

import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.integtests.nessie.NessieRefName;
import org.projectnessie.integtests.nessie.NessieTestsExtension;

@ExtendWith({NessieTestsExtension.class, IcebergSparkExtension.class})
public class ITIcebergSpark {

  @Test
  public void createBranch(@Spark SparkSession spark, @NessieRefName String dev) {
    System.err.println(
        spark.sql(format("CREATE BRANCH %s IN nessie FROM main", dev)).collectAsList());
    System.err.println(spark.sql("LIST REFERENCES IN nessie").collectAsList());
  }

  @Test
  public void listReferences(@Spark SparkSession spark) {
    System.err.println(spark.sql("LIST REFERENCES IN nessie").collectAsList());
  }

  @Test
  public void createTable(@Spark SparkSession spark) {
    spark.sql("CREATE TABLE nessie.db.tbl (id int, name string)");
    spark.sql("INSERT INTO nessie.db.tbl select 42, \"foo\"");
  }
}
