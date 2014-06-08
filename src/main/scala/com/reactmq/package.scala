package com

import java.net.InetSocketAddress
import akka.util.Timeout
import scala.concurrent.duration._
import com.typesafe.scalalogging.slf4j.StrictLogging
import java.nio.charset.Charset

package object reactmq {
  val serverAddress = new InetSocketAddress("localhost", 9182)

  implicit val timeout = Timeout(5.seconds)

  type Logging = StrictLogging

  val utf8charset = Charset.forName("UTF-8")
}
