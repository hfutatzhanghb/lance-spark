/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lance.spark.write;

import org.lance.Dataset;
import org.lance.WriteParams;
import org.lance.spark.LanceSparkWriteOptions;
import org.lance.spark.TestUtils;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.spark.sql.connector.write.BatchWrite;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.sql.util.LanceArrowUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AddColumnsBackfillWriteTest {
  @TempDir static Path tempDir;

  @Test
  public void testManagedVersioningIsPropagatedToBatchWrite(TestInfo testInfo) throws Exception {
    String datasetName = testInfo.getTestMethod().get().getName();
    String datasetUri = TestUtils.getDatasetUri(tempDir.toString(), datasetName);

    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      FieldType fieldType = FieldType.nullable(new ArrowType.Int(32, true));
      org.apache.arrow.vector.types.pojo.Field field =
          new org.apache.arrow.vector.types.pojo.Field("id", fieldType, null);
      Schema arrowSchema = new Schema(Collections.singletonList(field));
      Dataset.create(allocator, datasetUri, arrowSchema, new WriteParams.Builder().build()).close();

      LanceSparkWriteOptions writeOptions = LanceSparkWriteOptions.from(datasetUri);
      StructType sparkSchema = LanceArrowUtils.fromArrowSchema(arrowSchema);
      List<String> newColumns = Collections.singletonList("new_col");
      List<String> tableId = Collections.singletonList("table");
      String namespaceImpl = "test.namespace.impl";

      AddColumnsBackfillWrite.AddColumnsWriteBuilder builder =
          new AddColumnsBackfillWrite.AddColumnsWriteBuilder(
              sparkSchema,
              writeOptions,
              newColumns,
              null,
              namespaceImpl,
              Collections.emptyMap(),
              tableId,
              true);

      AddColumnsBackfillWrite write = (AddColumnsBackfillWrite) builder.build();
      BatchWrite batch = write.toBatch();

      assertInstanceOf(AddColumnsBackfillBatchWrite.class, batch);
      assertField(batch, "managedVersioning", true);
      assertField(batch, "namespaceImpl", namespaceImpl);
      assertField(batch, "tableId", tableId);
    }
  }

  @Test
  public void testManagedVersioningDefaultsThroughBuilder(TestInfo testInfo) throws Exception {
    String datasetName = testInfo.getTestMethod().get().getName();
    String datasetUri = TestUtils.getDatasetUri(tempDir.toString(), datasetName);

    try (BufferAllocator allocator = new RootAllocator(Long.MAX_VALUE)) {
      FieldType fieldType = FieldType.nullable(new ArrowType.Int(32, true));
      org.apache.arrow.vector.types.pojo.Field field =
          new org.apache.arrow.vector.types.pojo.Field("id", fieldType, null);
      Schema arrowSchema = new Schema(Collections.singletonList(field));
      Dataset.create(allocator, datasetUri, arrowSchema, new WriteParams.Builder().build()).close();

      LanceSparkWriteOptions writeOptions = LanceSparkWriteOptions.from(datasetUri);
      StructType sparkSchema = LanceArrowUtils.fromArrowSchema(arrowSchema);

      AddColumnsBackfillWrite.AddColumnsWriteBuilder builder =
          new AddColumnsBackfillWrite.AddColumnsWriteBuilder(
              sparkSchema,
              writeOptions,
              Collections.singletonList("new_col"),
              null,
              null,
              null,
              null,
              false);

      AddColumnsBackfillWrite write = (AddColumnsBackfillWrite) builder.build();
      BatchWrite batch = write.toBatch();

      assertInstanceOf(AddColumnsBackfillBatchWrite.class, batch);
      assertField(batch, "managedVersioning", false);
    }
  }

  private static void assertField(Object target, String fieldName, Object expected)
      throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    assertEquals(expected, field.get(target));
  }

  private static void assertField(Object target, String fieldName, boolean expected)
      throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    if (expected) {
      assertTrue(field.getBoolean(target));
    } else {
      assertFalse(field.getBoolean(target));
    }
  }
}
