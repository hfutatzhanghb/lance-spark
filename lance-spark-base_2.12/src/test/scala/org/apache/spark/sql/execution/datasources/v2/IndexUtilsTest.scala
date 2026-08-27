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
package org.apache.spark.sql.execution.datasources.v2

import org.apache.spark.sql.catalyst.plans.logical.LanceNamedArgument
import org.junit.jupiter.api.Assertions._
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.lance.index.IndexType

import scala.collection.mutable.ArrayBuffer

/**
 * Unit tests for [[IndexUtils]] helper methods.
 *
 * These tests are pure (no SparkSession, no Lance native library) and execute on the JVM only,
 * so they can run in any CI environment without native dependencies.
 */
class IndexUtilsTest {

  private def fragmentWorkloads(rows: Long*): List[FragmentWorkload] =
    rows.zipWithIndex.map { case (rowCount, fragmentId) =>
      FragmentWorkload(java.lang.Integer.valueOf(fragmentId), rowCount)
    }.toList

  // ── extractTrain ──────────────────────────────────────────────────────────

  @Test
  def extractTrain_defaultsToTrueWhenArgAbsent(): Unit = {
    assertTrue(IndexUtils.extractTrain(Seq.empty))
  }

  @Test
  def extractTrain_returnsTrueForExplicitTrue(): Unit = {
    val args = Seq(LanceNamedArgument("train", java.lang.Boolean.TRUE))
    assertTrue(IndexUtils.extractTrain(args))
  }

  @Test
  def extractTrain_returnsFalseForExplicitFalse(): Unit = {
    val args = Seq(LanceNamedArgument("train", java.lang.Boolean.FALSE))
    assertFalse(IndexUtils.extractTrain(args))
  }

  @Test
  def extractTrain_ignoresUnrelatedArgs(): Unit = {
    val args = Seq(
      LanceNamedArgument("base_tokenizer", "simple"),
      LanceNamedArgument("language", "English"))
    assertTrue(IndexUtils.extractTrain(args))
  }

  @Test
  def extractTrain_trainFalseAlongsideOtherArgs(): Unit = {
    val args = Seq(
      LanceNamedArgument("base_tokenizer", "simple"),
      LanceNamedArgument("train", java.lang.Boolean.FALSE))
    assertFalse(IndexUtils.extractTrain(args))
  }

  @Test
  def extractTrain_throwsOnNonBooleanValue(): Unit = {
    val args = Seq(LanceNamedArgument("train", "yes"))
    assertThrows(
      classOf[IllegalArgumentException],
      () => IndexUtils.extractTrain(args))
  }

  @Test
  def extractTrain_throwsOnIntegerValue(): Unit = {
    val args = Seq(LanceNamedArgument("train", java.lang.Integer.valueOf(1)))
    assertThrows(
      classOf[IllegalArgumentException],
      () => IndexUtils.extractTrain(args))
  }

  // ── toJson — SparkOnlyOptions filtering ───────────────────────────────────

  @Test
  def toJson_emptyArgsReturnsEmptyObject(): Unit = {
    assertEquals("{}", IndexUtils.toJson(Seq.empty))
  }

  @Test
  def toJson_filtersTrainFromOutput(): Unit = {
    val args = Seq(LanceNamedArgument("train", java.lang.Boolean.FALSE))
    assertEquals("{}", IndexUtils.toJson(args))
  }

  @Test
  def toJson_filtersBuildModeFromOutput(): Unit = {
    val args = Seq(LanceNamedArgument("build_mode", "range"))
    assertEquals("{}", IndexUtils.toJson(args))
  }

  @Test
  def toJson_filtersRowsPerRangeFromOutput(): Unit = {
    val args = Seq(LanceNamedArgument("rows_per_range", java.lang.Long.valueOf(500000L)))
    assertEquals("{}", IndexUtils.toJson(args))
  }

  @Test
  def toJson_filtersNumSegmentsFromOutput(): Unit = {
    val args = Seq(LanceNamedArgument("num_segments", java.lang.Integer.valueOf(4)))
    assertEquals("{}", IndexUtils.toJson(args))
  }

  @Test
  def toJson_filtersAllSparkOnlyOptionsLeavingIndexParams(): Unit = {
    val args = Seq(
      LanceNamedArgument("train", java.lang.Boolean.FALSE),
      LanceNamedArgument("build_mode", "range"),
      LanceNamedArgument("rows_per_range", java.lang.Long.valueOf(1000000L)),
      LanceNamedArgument("num_segments", java.lang.Integer.valueOf(8)),
      LanceNamedArgument("base_tokenizer", "simple"),
      LanceNamedArgument("language", "English"))
    val json = IndexUtils.toJson(args)
    assertFalse(json.contains("train"), "train must be stripped from JSON params")
    assertFalse(json.contains("build_mode"), "build_mode must be stripped from JSON params")
    assertFalse(json.contains("rows_per_range"), "rows_per_range must be stripped from JSON params")
    assertFalse(json.contains("num_segments"), "num_segments must be stripped from JSON params")
    assertTrue(json.contains("base_tokenizer"), "index param base_tokenizer must be present")
    assertTrue(json.contains("language"), "index param language must be present")
  }

  @Test
  def toJson_preservesStringParams(): Unit = {
    val args = Seq(LanceNamedArgument("base_tokenizer", "simple"))
    val json = IndexUtils.toJson(args)
    assertTrue(json.contains("\"base_tokenizer\""))
    assertTrue(json.contains("\"simple\""))
  }

  @Test
  def toJson_preservesBooleanParams(): Unit = {
    val args = Seq(LanceNamedArgument("with_position", java.lang.Boolean.TRUE))
    val json = IndexUtils.toJson(args)
    assertTrue(json.contains("\"with_position\""))
    assertTrue(json.contains("true"))
  }

  @Test
  def toJson_preservesLongParams(): Unit = {
    val args = Seq(LanceNamedArgument("zone_size", java.lang.Long.valueOf(64L)))
    val json = IndexUtils.toJson(args)
    assertTrue(json.contains("\"zone_size\""))
    assertTrue(json.contains("64"))
  }

  // ── buildIndexType ─────────────────────────────────────────────────────────

  @Test
  def buildIndexType_btreeCaseInsensitive(): Unit = {
    assertEquals(IndexType.BTREE, IndexUtils.buildIndexType("btree"))
    assertEquals(IndexType.BTREE, IndexUtils.buildIndexType("BTREE"))
    assertEquals(IndexType.BTREE, IndexUtils.buildIndexType("BTree"))
  }

  @Test
  def buildIndexType_ftsAndInvertedReturnInverted(): Unit = {
    assertEquals(IndexType.INVERTED, IndexUtils.buildIndexType("fts"))
    assertEquals(IndexType.INVERTED, IndexUtils.buildIndexType("FTS"))
    assertEquals(IndexType.INVERTED, IndexUtils.buildIndexType("inverted"))
    assertEquals(IndexType.INVERTED, IndexUtils.buildIndexType("INVERTED"))
  }

  @Test
  def buildScalarIndexParamType_ftsAndInvertedReturnInverted(): Unit = {
    assertEquals("inverted", IndexUtils.buildScalarIndexParamType("fts"))
    assertEquals("inverted", IndexUtils.buildScalarIndexParamType("inverted"))
  }

  @Test
  def scalarSegmentIndexType_mapsAllSupportedMethods(): Unit = {
    val expected = Seq(
      ("zonemap", IndexType.ZONEMAP, "zonemap"),
      ("bitmap", IndexType.BITMAP, "bitmap"),
      ("label_list", IndexType.LABEL_LIST, "labellist"),
      ("ngram", IndexType.NGRAM, "ngram"),
      ("bloomfilter", IndexType.BLOOM_FILTER, "bloomfilter"),
      ("rtree", IndexType.RTREE, "rtree"),
      ("fts", IndexType.INVERTED, "inverted"),
      ("inverted", IndexType.INVERTED, "inverted"))

    expected.foreach { case (method, indexType, coreParamType) =>
      Seq(method, method.toUpperCase).foreach { spelling =>
        assertEquals(indexType, IndexUtils.scalarSegmentIndexType(spelling).get)
        assertEquals(indexType, IndexUtils.buildIndexType(spelling))
        assertEquals(coreParamType, IndexUtils.buildScalarIndexParamType(spelling))
      }
    }
  }

  @Test
  def scalarSegmentIndexType_rejectsAliases(): Unit = {
    Seq("labellist", "bloom_filter", "r_tree").foreach { alias =>
      assertTrue(IndexUtils.scalarSegmentIndexType(alias).isEmpty)
      assertThrows(
        classOf[UnsupportedOperationException],
        () => IndexUtils.buildIndexType(alias))
    }
  }

  @Test
  def buildIndexType_throwsOnUnknown(): Unit = {
    assertThrows(
      classOf[UnsupportedOperationException],
      () => IndexUtils.buildIndexType("ivf_pq"))
  }

  @Test
  def batchFragments_respectsNumSegmentsAndDefaults(): Unit = {
    val fragments = fragmentWorkloads(1, 1, 1, 1)

    assertEquals(Seq(2, 2), IndexUtils.batchFragments(fragments, Some(2), 4).map(_.size))
    assertEquals(4, IndexUtils.batchFragments(fragments, Some(4), 4).size)
    assertEquals(4, IndexUtils.batchFragments(fragments, Some(10), 4).size)
    assertEquals(4, IndexUtils.batchFragments(fragments, None, 8).size)
    assertEquals(2, IndexUtils.batchFragments(fragments, None, 2).size)
    assertEquals(Seq.empty, IndexUtils.batchFragments(Nil, None, 4))
  }

  @Test
  def batchFragments_balancesRowsDeterministically(): Unit = {
    val fragments = fragmentWorkloads(80, 50, 30, 20)
    val expected = Seq(
      List(java.lang.Integer.valueOf(0), java.lang.Integer.valueOf(3)),
      List(java.lang.Integer.valueOf(1), java.lang.Integer.valueOf(2)))

    assertEquals(expected, IndexUtils.batchFragments(fragments, Some(2), 4))
    assertEquals(expected, IndexUtils.batchFragments(fragments.reverse, Some(2), 4))
  }

  @Test
  def batchFragments_distributesZeroRowFragmentsAcrossSegments(): Unit = {
    val fragments = fragmentWorkloads(0, 0, 0, 0).reverse

    assertEquals(
      Seq(
        List(java.lang.Integer.valueOf(0), java.lang.Integer.valueOf(3)),
        List(java.lang.Integer.valueOf(1)),
        List(java.lang.Integer.valueOf(2))),
      IndexUtils.batchFragments(fragments, Some(3), 4))
  }

  @Test
  def batchFragments_rejectsWorkloadOverflow(): Unit = {
    assertThrows(
      classOf[ArithmeticException],
      () => IndexUtils.batchFragments(fragmentWorkloads(Long.MaxValue, 1), Some(1), 1))
  }

  @Test
  def indexSegmentProgress_reportsSuccessfulPartitionsOnce(): Unit = {
    var completed = 0L
    var total = 0L
    val logs = ArrayBuffer.empty[String]
    val publishedSnapshots = ArrayBuffer.empty[(Long, Long)]
    val progress = new SparkIndexSegmentProgress(
      "idx_text",
      delta => completed += delta,
      delta => total += delta,
      () => publishedSnapshots += completed -> total,
      message => logs += message,
      (_, _) => ())

    progress.start(3)
    progress.segmentComplete(2)
    progress.segmentComplete(0)
    progress.segmentComplete(2)
    progress.segmentComplete(1)

    assertEquals(3L, total)
    assertEquals(
      3L,
      completed,
      "a retried or duplicate partition result must not increment progress twice")
    assertEquals(
      Seq(0L -> 3L, 1L -> 3L, 2L -> 3L, 3L -> 3L),
      publishedSnapshots.toSeq,
      "publish a start snapshot and one snapshot per unique successful partition")
    assertTrue(logs.head.contains("started"))
    assertTrue(logs.last.contains("3/3 segments completed"))
  }

  @Test
  def indexSegmentProgress_isolatesPublicationFailures(): Unit = {
    var completed = 0L
    var total = 0L
    var logs = 0
    var warnings = 0
    val progress = new SparkIndexSegmentProgress(
      "idx_text",
      delta => completed += delta,
      delta => total += delta,
      () => throw new RuntimeException("publication failed"),
      _ => logs += 1,
      (_, _) => warnings += 1)

    assertProgressDoesNotThrow {
      progress.start(2)
    }
    assertProgressDoesNotThrow {
      progress.segmentComplete(0)
    }

    assertEquals(2L, total)
    assertEquals(1L, completed)
    assertEquals(2, logs, "publication failures must not suppress progress logging")
    assertEquals(2, warnings, "each failed publication should be reported once")
  }

  @Test
  def indexSegmentProgress_publishesWhenOtherObservationCallbacksFail(): Unit = {
    var publications = 0
    var warnings = 0
    val progress = new SparkIndexSegmentProgress(
      "idx_text",
      _ => throw new RuntimeException("metric update failed"),
      _ => throw new RuntimeException("metric update failed"),
      () => publications += 1,
      _ => throw new RuntimeException("log failed"),
      (_, _) => warnings += 1)

    assertProgressDoesNotThrow {
      progress.start(2)
    }
    assertProgressDoesNotThrow {
      progress.segmentComplete(0)
    }

    assertEquals(2, publications, "metric or log failures must not suppress publication attempts")
    assertEquals(4, warnings)
  }

  @Test
  def indexSegmentProgress_ignoresObservationFailures(): Unit = {
    var warnings = 0
    val progress = new SparkIndexSegmentProgress(
      "idx_text",
      _ => throw new RuntimeException("metric update failed"),
      _ => throw new RuntimeException("metric update failed"),
      () => throw new RuntimeException("publication failed"),
      _ => throw new RuntimeException("log failed"),
      (_, _) => {
        warnings += 1
        throw new RuntimeException("warn failed")
      })

    assertProgressDoesNotThrow {
      progress.start(2)
    }
    assertProgressDoesNotThrow {
      progress.segmentComplete(0)
    }
    assertEquals(6, warnings)
  }

  @Test
  def indexSegmentMetricDefinitions_onlyReportsForEagerSegmentBuilds(): Unit = {
    val ftsMetricNames =
      AddIndexExec.indexSegmentMetricDefinitions("fts", Seq.empty).keySet

    assertEquals(
      Set(
        AddIndexExec.INDEX_BUILD_COMPLETED_SEGMENTS,
        AddIndexExec.INDEX_BUILD_TOTAL_SEGMENTS),
      ftsMetricNames)
    assertFalse(AddIndexExec.indexSegmentMetricDefinitions("BTREE", Seq.empty).isEmpty)
    assertTrue(
      AddIndexExec.indexSegmentMetricDefinitions(
        "btree",
        Seq(LanceNamedArgument("build_mode", "range"))).isEmpty)
    assertTrue(
      AddIndexExec.indexSegmentMetricDefinitions(
        "fts",
        Seq(LanceNamedArgument("train", java.lang.Boolean.FALSE))).isEmpty)
    assertTrue(AddIndexExec.indexSegmentMetricDefinitions("ivf_pq", Seq.empty).isEmpty)
  }

  private def assertProgressDoesNotThrow(callback: => Unit): Unit = {
    assertDoesNotThrow(new Executable {
      override def execute(): Unit = callback
    })
  }
}
