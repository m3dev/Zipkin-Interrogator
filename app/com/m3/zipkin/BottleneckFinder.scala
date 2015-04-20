package com.m3.zipkin

import com.twitter.zipkin.common.Span
import com.twitter.zipkin.thriftscala.TraceCombo
import org.joda.time.Interval

import scala.collection.mutable
import scala.concurrent.duration._

import com.twitter.zipkin.conversions.thrift._

trait BottleneckFinder {

  def namesBlacklist: Seq[String]

  /**
   * Returns a sequence of SpanTrees in the provided TraceCombo that are considered "critical"
   *
   * Overall, the algorithm is fairly "bubbly" and definitely has room for improvement, but for simply
   * our purposes has proven to be "fast enough" please feel free to optimise.
   *
   * @param traceCombo the root trace combo
   * @param unignorableDuration duration at which a span is considered to be critical no matter what
   * @param overlapPercentageThreshold the lower bound percentage of overlap that a span has with another span at
   *                                   which it will be considered to be non-overlapping
   */
  def criticalOnly(
    traceCombo: TraceCombo,
    ignorableDurationMax: Duration = 5.millis,
    unignorableDuration: Duration = 1.second,
    overlapPercentageThreshold: Float = 0.2F
  ): Seq[AnalysedSpanEntryTree] = {
    val trace = traceCombo.trace.toTrace
    val childMap = trace.getIdToChildrenMap
    val spanMap = trace.getIdToSpanMap
    val roots = trace.getRootSpans(spanMap)
    val nonOverlappingRoots = criticalSpans(roots, ignorableDurationMax, unignorableDuration, overlapPercentageThreshold)
    nonOverlappingRoots.map { root =>
      getBottleneckTree(root, childMap, ignorableDurationMax, unignorableDuration, overlapPercentageThreshold)
    }
  }

  protected def getBottleneckTree(
    analysedSpan: AnalysedSpan,
    idToChildren: mutable.MultiMap[Long, Span],
    ignorableDurationMax: Duration,
    unignorableDuration: Duration,
    overlapPercentageThreshold: Float
  ): AnalysedSpanEntryTree = {
    val children = idToChildren.get(analysedSpan.span.id).toSeq.flatMap(_.toSeq).sortBy(_.firstTimestamp.getOrElse(0L))
    val criticalChildren = criticalSpans(children, ignorableDurationMax, unignorableDuration, overlapPercentageThreshold)

    criticalChildren match {
      case x if x.nonEmpty => {
        AnalysedSpanEntryTree(
          analysedSpan = analysedSpan,
          children = x.map(span =>
            getBottleneckTree(
              analysedSpan = span,
              idToChildren = idToChildren,
              ignorableDurationMax = ignorableDurationMax,
              unignorableDuration = unignorableDuration,
              overlapPercentageThreshold = overlapPercentageThreshold
            )).toList
        )
      }
      case Nil => AnalysedSpanEntryTree(analysedSpan, List[AnalysedSpanEntryTree]())
    }
  }

  protected def criticalSpans(
    spans: Seq[Span],
    ignorableDurationMax: Duration,
    unignorableDuration: Duration,
    overlapPercentageThreshold: Float
  ): Seq[AnalysedSpan] = {
    val usefulSpans = spans.filter { s =>
      namesBlacklist.forall(!s.name.contains(_)) && s.duration.exists(_ >= ignorableDurationMax.toMicros)
    }
    val spansToOverlaps = spansToPercentageOverlaps(usefulSpans)
    usefulSpans.collect {
      case s if spans.filter(_ != s).forall { otherSpan =>
        spansToOverlaps.getOrElse((s, otherSpan), 1F) <= spansToOverlaps.getOrElse((otherSpan, s), 1F) ||
          spansToOverlaps.getOrElse((s, otherSpan), 1F) <= overlapPercentageThreshold
      } => AnalysedSpan(s, CriticalSpan)
      case s if s.duration.exists(_ >= unignorableDuration.toMicros) => AnalysedSpan(s, UnignorableDuration)
    }
  }

  private def overlapPercentages(span1: Span, span2: Span): (SpanOverlap, SpanOverlap) = {
    val span1Interval = new Interval(span1.firstTimestamp.getOrElse(0L) / 1000, span1.lastTimestamp.getOrElse(1L) / 1000)
    val span2Interval = new Interval(span2.firstTimestamp.getOrElse(0L) / 1000, span2.lastTimestamp.getOrElse(1L) / 1000)
    val overlap = Option(span1Interval.overlap(span2Interval)).map(_.toDurationMillis)
    overlap match {
      case Some(interval) if span1Interval.toDurationMillis > 0 && span2Interval.toDurationMillis > 0 => {
        (
          SpanOverlap(of = span1, by = span2, overlapPercentage = interval / span1Interval.toDurationMillis.toFloat),
          SpanOverlap(of = span2, by = span1, overlapPercentage = interval / span2Interval.toDurationMillis.toFloat)
        )
      }
      case _ => {
        (
          SpanOverlap(of = span1, by = span2, overlapPercentage = 0),
          SpanOverlap(of = span2, by = span1, overlapPercentage = 0)
        )
      }
    }
  }

  private def spansToPercentageOverlaps(spans: Seq[Span]): Map[(Span, Span), Float] = {
    spans.foldLeft(Map.empty[(Span, Span), Float]) {
      case (acc, currentSpan) =>
        val overlapPercs = for {
          otherSpan <- spans
          if otherSpan != currentSpan && !acc.contains((currentSpan, otherSpan))
        } yield {
          overlapPercentages(currentSpan, otherSpan)
        }
        acc ++ overlapPercs.flatMap {
          case (currentByOther, otherByCurrent) => {
            Seq(
              (currentByOther.of, currentByOther.by) -> currentByOther.overlapPercentage,
              (otherByCurrent.of, otherByCurrent.by) -> otherByCurrent.overlapPercentage
            )
          }
        }
    }
  }

  // Simple class to keep things clear
  private case class SpanOverlap(of: Span, by: Span, overlapPercentage: Float)

}