package com.m3.zipkin

import com.twitter.util.{ Future => TwitterF }
import scala.concurrent.{ Future => ScalaF, Promise => ScalaP }
import scala.language.implicitConversions

object TwitterFutureSupport {
  /**
   * Implicit conversion from a Twitter Future to a Scala Future
   */
  implicit def twitterFutureToScalaFuture[T](twitterF: TwitterF[T]): ScalaF[T] = {
    val scalaP = ScalaP[T]
    twitterF.onSuccess { r: T =>
      scalaP.success(r)
    }
    twitterF.onFailure { e: Throwable =>
      scalaP.failure(e)
    }
    scalaP.future
  }
}

