package com

import java.net.InetSocketAddress
import com.typesafe.scalalogging.slf4j.StrictLogging

package object reactmq {
  val sendServerAddress     = new InetSocketAddress("localhost", 9181)
  val receiveServerAddress  = new InetSocketAddress("localhost", 9182)

  type Logging = StrictLogging
}
