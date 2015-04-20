package com.m3.zipkin

import com.twitter.finagle.Thrift
import com.twitter.zipkin.thriftscala.ZipkinQuery

/**
 * Created by Lloyd on 4/20/15.
 */
object ZipkinQueryClient {

  /**
   * Returns a ZipkinClient for the given host and port
   */
  def apply(host: String, port: String): ZipkinQuery.FutureIface = {
    Thrift.newIface[ZipkinQuery.FutureIface]("ZipkinQuery=" + s"$host:$port")
  }

}