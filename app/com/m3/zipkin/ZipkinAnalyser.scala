package com.m3.zipkin

import com.twitter.util.{ Time, Future }
import com.twitter.zipkin.query.Trace
import com.twitter.zipkin.thriftscala.{ Order, TraceCombo, ZipkinQuery }
import com.twitter.zipkin.conversions.thrift._
import play.api.{ Configuration, Play }

import scala.collection.Set

/**
 * Wraps the Zipkin Thrift interface, adding on top some Span analytic functions
 *
 * @param client implementation of the Zipkin Thrift interface
 * @param config Play config
 */
class ZipkinAnalyser(client: ZipkinQuery.FutureIface, config: Configuration)
    extends BottleneckFinder
    with SpanTreePrintingSupport {

  val namesBlacklist = config.getStringSeq("zipkin.analyser.blackList").getOrElse(Nil)

  /**
   * Gets the names of all the services that have Zipkin records
   */
  def services: Future[Set[String]] = client.getServiceNames()

  def traceIdsByServiceSpan(
    service: String,
    spanName: String,
    limit: Int,
    endTs: Long = Time.now.inNanoseconds,
    order: Order = Order.DurationDesc
  ): Future[Seq[(Long, Trace)]] = {
    for {
      traceIds <- client.getTraceIdsBySpanName(
        serviceName = service,
        spanName = spanName,
        limit = limit,
        endTs = endTs,
        order = order
      )
      traces <- client.getTracesByIds(traceIds)
    } yield {
      val niceTracesSorted = traces.map(_.toTrace)
      val traceIdsToTraces = for {
        traceId <- traceIds
        trace <- niceTracesSorted.find(_.id.contains(traceId))
      } yield {
        traceId -> trace
      }
      traceIdsToTraces.sortBy(_._2.duration).reverse
    }
  }

  /**
   * Returns the Spans for a given Service
   */
  def spansForService(service: String): Future[Set[String]] = {
    client.getSpanNames(service)
  }

  /**
   * Gets Traces by ids
   */
  def traceComboByIds(ids: Seq[Long]): Future[Seq[TraceCombo]] = {
    client.getTraceCombosByIds(ids)
  }

}