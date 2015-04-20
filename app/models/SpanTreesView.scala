package models

import com.m3.zipkin.{ AnalysedSpanEntryTree, SpanTreePrintingSupport }
import com.twitter.zipkin.query.{ SpanTreeEntry, TraceCombo }

case class SpanTreesView(trees: Seq[AnalysedSpanEntryTree], traceCombo: TraceCombo) extends SpanTreePrintingSupport {

  def pre: Seq[String] = trees.flatMap(toPre(_, 0))
}
