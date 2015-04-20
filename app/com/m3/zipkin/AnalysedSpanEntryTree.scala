package com.m3.zipkin

import com.twitter.zipkin.common.Span

/**
 * Holds a Span and the reason why it is being held
 */
case class AnalysedSpan(span: Span, issue: AnalysedIssue)

/**
 * Basically the same as a SpanEntryTree from Twitter but holds AnalysedSpans
 */
case class AnalysedSpanEntryTree(analysedSpan: AnalysedSpan, children: List[AnalysedSpanEntryTree]) {

  def toList: List[AnalysedSpan] = {
    childrenToList(this)
  }

  private def childrenToList(entry: AnalysedSpanEntryTree): List[AnalysedSpan] = {
    entry.children match {
      case Nil =>
        List[AnalysedSpan](entry.analysedSpan)

      case someChildren =>
        val sorted = someChildren.sortBy(_.analysedSpan.span.firstAnnotation.map(_.timestamp))
        entry.analysedSpan :: sorted.map(childrenToList).flatten
    }
  }

}
