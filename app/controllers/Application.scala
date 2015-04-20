package controllers

import java.math.BigInteger
import java.util.Date

import com.m3.zipkin.{ SpanTreePrinter, ZipkinAnalyser, ZipkinQueryClient }
import com.m3.zipkin.TwitterFutureSupport._
import com.twitter.zipkin.query.Trace
import com.twitter.zipkin.conversions.thrift._
import models.{ SpanTreesView, LinkView, BreadCrumbs }
import org.ocpsoft.prettytime.PrettyTime
import play.api.Play
import play.api.cache.Cached

import play.api.mvc.{ Call, Action, Controller }
import views.helpers.FlashLevel
import views.html._

import scala.concurrent.duration._

object Application extends Controller {

  val traceLimit = 100

  import play.api.Play.current

  lazy val analyser = {
    val zipkinQueryServerHost = Play.configuration.underlying.getString("zipkin.queryHost")
    val zipkinQueryServerPort = Play.configuration.underlying.getString("zipkin.queryPort")
    val client = ZipkinQueryClient(host = zipkinQueryServerHost, port = zipkinQueryServerPort)
    new ZipkinAnalyser(client, current.configuration)
  }

  lazy val webUiHost = Play.configuration.underlying.getString("zipkin.webUiHost").stripSuffix("/")
  lazy val ignorableTraceDuration = Play.configuration.getInt("zipkin.analyser.ignorableTracesMs").getOrElse(10).millis
  lazy val ignorableSpanDuration = Play.configuration.getInt("zipkin.analyser.ignorableSpansMs").getOrElse(50).millis

  def index = Action.async { implicit req =>
    val fServices = analyser.services
    val breadcrumbs = BreadCrumbs(LinkView("Home", routes.Application.index()))
    fServices.map { services =>
      Ok(
        main(
          "Home",
          breadcrumbs,
          urlList(
            "Services",
            None,
            services.toSeq.sorted.map(s => LinkView(s, routes.Application.serviceSpans(s)))
          )
        )
      )
    }
  }

  def serviceSpans(serviceName: String) = Action.async { implicit req =>
    val fSpanNames = analyser.spansForService(serviceName)
    val breadcrumbs = BreadCrumbs(
      LinkView("Home", routes.Application.index()),
      LinkView(serviceName, routes.Application.serviceSpans(serviceName))
    )
    fSpanNames.map { spanNames =>
      Ok(
        main(
          s"Service: $serviceName",
          breadcrumbs,
          urlList(
            serviceName,
            Some("Spans"),
            spanNames.toSeq.sorted.map(s => LinkView(s, routes.Application.spanNameTraceIds(serviceName, s)))
          )
        )
      )
    }
  }

  // Since this is fairly intense, we cache it for a few seconds to prevent us from taking down our Zipkin Query service
  def spanNameTraceIds(serviceName: String, spanName: String) = Cached.status(
    key = _ => s"spanNameTraceIds/$serviceName/$spanName",
    status = OK,
    duration = 5
  ) {
    Action.async { implicit req =>
      val fTraceIds = analyser.traceIdsByServiceSpan(serviceName, spanName, traceLimit)
      val breadcrumbs = BreadCrumbs(
        LinkView("Home", routes.Application.index()),
        LinkView(serviceName, routes.Application.serviceSpans(serviceName)),
        LinkView(spanName, routes.Application.spanNameTraceIds(serviceName, spanName))
      )
      fTraceIds.map { traceIdsWithTraces =>
        val sigTraces = traceIdsWithTraces.filter(_._2.duration > ignorableTraceDuration.toMicros)
        Ok(
          main(
            s"Service: $serviceName, Span: $spanName",
            breadcrumbs,
            urlList(
              spanName,
              Some(s"Trace ids, slowest first, last ${sigTraces.size}"),
              buildTraceLinks(serviceName = serviceName, spanName = spanName, traceIdsToTraces = sigTraces),
              Some(s"No traces here. Traces under ${ignorableTraceDuration.toMillis} ms are filtered out")
            )
          )
        )
      }
    }
  }

  def traceAnalysis(serviceName: String, spanName: String, traceId: String) = Action.async { implicit req =>
    val fTraceCombo = analyser.traceComboByIds(Seq(hexToLong(traceId)))
    val breadcrumbs = BreadCrumbs(
      LinkView("Home", routes.Application.index()),
      LinkView(serviceName, routes.Application.serviceSpans(serviceName)),
      LinkView(spanName, routes.Application.spanNameTraceIds(serviceName, spanName)),
      LinkView(traceId, routes.Application.traceAnalysis(serviceName, spanName, traceId))
    )
    val webUiLink = toWebUiLink(traceId)
    fTraceCombo.map { traceCombos =>
      if (traceCombos.isEmpty)
        Redirect(routes.Application.spanNameTraceIds(serviceName, spanName)).flashing(Map(FlashLevel.error -> s"Could not fetch data for traceId $traceId, please try again"))
      else {
        val traceCombo = traceCombos.head.toTraceCombo
        val trace = traceCombo.trace
        val duration = trace.duration.micros
        val happenedAt = SpanTreePrinter.traceHappenedAt(trace)
        Ok(
          main(
            s"Service: $serviceName, Span: $spanName, TraceId: $traceId",
            breadcrumbs,
            traceShow(
              spanName,
              traceId,
              duration,
              happenedAt,
              serviceName,
              SpanTreesView(
                analyser.criticalOnly(traceCombo = traceCombos.head, ignorableDurationMax = ignorableSpanDuration),
                traceCombo
              ),
              webUiLink
            )
          )
        )
      }

    }
  }

  private def longToHex(l: Long): String = java.lang.Long.toHexString(l)
  private def hexToLong(h: String): Long = new BigInteger(h, 16).longValue()
  private def toWebUiLink(traceId: String): LinkView = LinkView(s"View $traceId on Zipkin-Web-UI", Call("GET", s"$webUiHost/traces/$traceId"))

  private def buildTraceLinks(serviceName: String, spanName: String, traceIdsToTraces: Seq[(Long, Trace)]): Seq[LinkView] = {
    traceIdsToTraces.map { i =>
      val (id, trace) = i
      val duration = s"Took ${trace.duration.micros.toMillis} ms"
      val when = SpanTreePrinter.traceHappenedAt(trace)
      val subLabel = when.map(w => s"$duration, ended $w").orElse(Some(duration))
      LinkView(s"${longToHex(id)}", routes.Application.traceAnalysis(serviceName, spanName, longToHex(id)), subLabel)
    }
  }

}
