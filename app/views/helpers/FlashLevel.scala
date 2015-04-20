package views.helpers

import play.api.mvc.Flash
import scala.language.implicitConversions

/**
 * Created by Lloyd on 11/25/14.
 */

object FlashLevel extends Enumeration {

  val danger, success, info, warning, error = Value

  private val bootstrapAlertPrefix = "alert"

  /**
   * Returns a Bootstrap class string for [[Value]]
   */
  def bootstrapClassFor(v: FlashLevel.Value): String = v match {
    case `danger` => s"$bootstrapAlertPrefix-danger"
    case `success` => s"$bootstrapAlertPrefix-success"
    case `info` => s"$bootstrapAlertPrefix-info"
    case `warning` => s"$bootstrapAlertPrefix-warning"
    case `error` => s"$bootstrapAlertPrefix-danger"
    case _ => s"$bootstrapAlertPrefix-danger"
  }

  implicit def flashLevelToStringMap(flash: Map[FlashLevel.Value, String]): Flash =
    Flash(flash.map { case (v, k) => v.toString -> k })

}