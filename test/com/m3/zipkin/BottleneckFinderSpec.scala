package com.m3.zipkin

import com.twitter.zipkin.common.{ Annotation, Span }
import com.twitter.zipkin.query.Trace
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala.TraceCombo
import org.scalatest.{ Matchers, FunSpec }

import scala.util.Random
import scala.concurrent.duration._

class BottleneckFinderSpec extends FunSpec with Matchers {

  val subject = new BottleneckFinder {
    val namesBlacklist: Seq[String] = Nil
  }

  val nonOverlappingSpans = {
    val traceId = 123L
    // Note that the spans have annotations that do not overlap with each other
    val span1Timespan = (1L, 1L + 10.millis.toMicros)
    val span2Timespan = (span1Timespan._2, span1Timespan._2 + 50.millis.toMicros)
    val span3Timespan = (span2Timespan._2, span2Timespan._2 + 50.millis.toMicros)
    Seq(
      Span(traceId, "first", Random.nextLong(), None, List(Annotation(span1Timespan._1, "cs", None), Annotation(span1Timespan._2, "cr", None)), Nil),
      Span(traceId, "second", Random.nextLong(), None, List(Annotation(span2Timespan._1, "cs", None), Annotation(span2Timespan._2, "cr", None)), Nil),
      Span(traceId, "third", Random.nextLong(), None, List(Annotation(span3Timespan._1, "cs", None), Annotation(span3Timespan._2, "cr", None)), Nil)
    )
  }

  describe("#criticalOnly") {

    it("should ignore spans that are under the ignorable duration setting") {
      val trace = Trace(nonOverlappingSpans)
      val traceCombo = TraceCombo(trace.toThrift)
      val r = subject.criticalOnly(traceCombo, ignorableDurationMax = 50.minutes)
      r shouldBe 'empty
    }

    describe("when passed a TraceCombo with spans that do not overlap") {

      it("should return all the spans") {
        val trace = Trace(nonOverlappingSpans)
        val traceCombo = TraceCombo(trace.toThrift)
        val r = subject.criticalOnly(traceCombo)
        r.size shouldBe nonOverlappingSpans.size
      }

    }

    describe("when passed a TraceCombo with some spans that overlap") {

      val lastSpan = nonOverlappingSpans.last
      val lastSpanLastAnnotation = lastSpan.annotations.last
      val longLastSpan = lastSpan.copy(
        id = Random.nextLong(),
        annotations = lastSpan.annotations :+ lastSpanLastAnnotation.copy(timestamp = lastSpanLastAnnotation.timestamp + 500.millis.toMicros)
      )
      val spans = nonOverlappingSpans :+ longLastSpan
      val trace = Trace(spans)
      val traceCombo = TraceCombo(trace.toThrift)

      it("should return only the non-overlapping spans along with the longer of the overlapping spans") {
        val r = subject.criticalOnly(traceCombo)
        r.size shouldBe (spans.size - 1) // because we only keep the last one that is mega long and overlapping
        r.last.analysedSpan.span shouldBe longLastSpan // instead of lastSpan, because it is now being covered by longLastSpan
      }

      it("should include overlapped spans if they are longer than the unignorable duration setting") {
        val r = subject.criticalOnly(traceCombo, unignorableDuration = 10.millis)
        r.size shouldBe spans.size
        r.map(_.analysedSpan.span) should contain theSameElementsAs spans
        r.map(_.analysedSpan.issue) should contain(UnignorableDuration)
      }

      it("should include overlapped spans if the amount of overlap is less than the overlap percentage threshold") {
        val minimallyOverlappingSpans = {
          val traceId = 123L
          // Note that the spans have annotations that overlap by 1 millisecond
          val span1Timespan = (1L, 1L + 10.millis.toMicros)
          val span2Timespan = (span1Timespan._2 - 1.millis.toMicros, span1Timespan._2 + 50.millis.toMicros)
          val span3Timespan = (span2Timespan._2 - 1.millis.toMicros, span2Timespan._2 + 50.millis.toMicros)
          Seq(
            Span(traceId, "first", Random.nextLong(), None, List(Annotation(span1Timespan._1, "cs", None), Annotation(span1Timespan._2, "cr", None)), Nil),
            Span(traceId, "second", Random.nextLong(), None, List(Annotation(span2Timespan._1, "cs", None), Annotation(span2Timespan._2, "cr", None)), Nil),
            Span(traceId, "third", Random.nextLong(), None, List(Annotation(span3Timespan._1, "cs", None), Annotation(span3Timespan._2, "cr", None)), Nil)
          )
        }
        val trace = Trace(minimallyOverlappingSpans)
        val traceCombo = TraceCombo(trace.toThrift)

        // Check for inclusion based on argument
        val r = subject.criticalOnly(traceCombo, overlapPercentageThreshold = 1.millis.toMillis / 10.millis.toMillis.toFloat)
        r.size shouldBe minimallyOverlappingSpans.size
        r.map(_.analysedSpan.span) should contain theSameElementsAs minimallyOverlappingSpans

        // Check for exclusion based on argument
        val rStricter = subject.criticalOnly(traceCombo, overlapPercentageThreshold = 1.millis.toMillis / 50.millis.toMillis.toFloat)
        rStricter.size shouldBe (minimallyOverlappingSpans.size - 1) // lower overlap threshold eliminates the first one
        rStricter.map(_.analysedSpan.span) should contain theSameElementsAs minimallyOverlappingSpans.tail
      }

    }

  }

}