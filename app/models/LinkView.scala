package models

import play.api.mvc.Call

case class LinkView(label: String, call: Call, subLabel: Option[String] = None)