package com.reactmq

import java.net.InetSocketAddress

import akka.actor.ActorSystem

trait SimpleServerSupport {
  implicit val system = ActorSystem()
  val sendServerAddress     = new InetSocketAddress("localhost", 9182)
  val receiveServerAddress  = new InetSocketAddress("localhost", 9183)
}
