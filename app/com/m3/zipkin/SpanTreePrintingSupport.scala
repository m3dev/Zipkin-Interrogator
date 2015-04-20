package com.m3.zipkin

import java.util.Date

import com.twitter.zipkin.common.{ AnnotationType, BinaryAnnotation, Span }
import com.twitter.zipkin.query.{ Trace, SpanTreeEntry }
import org.ocpsoft.prettytime.PrettyTime

trait SpanTreePrintingSupport {

  val prettyTime = new PrettyTime()

  /**
   * Print the full trace tree with indentation to give an overview.
   */
  def printTree(tree: SpanTreeEntry, indent: Int) {
    toPre(tree, indent).map(println)
  }

  def toPre(tree: SpanTreeEntry, indent: Int): Seq[String] = {
    val querySpan = tree.span
    val spanAnnotations = querySpan.annotations.map(a => s"value:${a.value}, duration:${a.duration}")
    val spanBinaryAnnotations = querySpan.binaryAnnotations.map(binAnnotationConv)
    val spanDescription = s"${" " * indent}spanId: ${querySpan.id}}, name:${querySpan.name}, duration:${querySpan.duration}, firstTimestamp: ${querySpan.firstTimestamp}}, lastTimestamp:${querySpan.lastTimestamp}, annotations: $spanAnnotations, binaryAnnotations: ${spanBinaryAnnotations}"
    spanDescription +: tree.children.flatMap(s => toPre(s, indent + 2))
  }

  def toPre(tree: AnalysedSpanEntryTree, indent: Int): Seq[String] = {
    val querySpan = tree.analysedSpan.span
    val spanAnnotations = querySpan.annotations.map(a => s"value:${a.value}, duration:${a.duration}")
    val spanBinaryAnnotations = querySpan.binaryAnnotations.map(binAnnotationConv)
    val spanDescription = s"${" " * indent}spanId: ${querySpan.id}}, name:${querySpan.name}, duration:${querySpan.duration}, issue: ${tree.analysedSpan.issue} firstTimestamp: ${querySpan.firstTimestamp}}, lastTimestamp:${querySpan.lastTimestamp}, annotations: $spanAnnotations, binaryAnnotations: ${spanBinaryAnnotations}"
    spanDescription +: tree.children.flatMap(s => toPre(s, indent + 2))
  }

  private def spanDesc(span: Span) = {
    s"${span.name} ${span.binaryAnnotations.map(binAnnotationConv)}"
  }

  def binAnnotationConv(b: BinaryAnnotation): Any = {
    b.annotationType match {
      case AnnotationType(0, _) => if (b.value.get() != 0) true else false // bool
      case AnnotationType(1, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // bytes
      case AnnotationType(2, _) => b.value.getShort // i16
      case AnnotationType(3, _) => b.value.getInt // i32
      case AnnotationType(4, _) => b.value.getLong // i64
      case AnnotationType(5, _) => b.value.getDouble // double
      case AnnotationType(6, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // string
      case _ => {
        throw new Exception("Unsupported annotation type: %s".format(b))
      }
    }
  }

  def traceHappenedAt(trace: Trace): Option[String] = {
    for {
      root <- trace.getRootSpan
      lastTime <- root.lastTimestamp
    } yield { prettyTime.format(new Date(lastTime / 1000)) }
  }

}

object SpanTreePrinter extends SpanTreePrintingSupport