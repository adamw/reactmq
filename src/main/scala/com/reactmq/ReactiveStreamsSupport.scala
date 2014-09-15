package com.reactmq

import akka.actor.ActorSystem
import akka.stream.scaladsl2.FlowMaterializer
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Future
import java.net.InetSocketAddress

trait ReactiveStreamsSupport extends Logging {
  implicit val system = ActorSystem()
  implicit val dispatcher = system.dispatcher

  implicit val timeout = Timeout(5.seconds)

  implicit val materializer = FlowMaterializer()

  def handleIOFailure(ioFuture: Future[Any], msg: => String) {
    ioFuture.onFailure {
      case e: Exception =>
        logger.error(msg, e)
        system.shutdown()
    }
  }

  val sendServerAddress     = new InetSocketAddress("localhost", 9182)
  val receiveServerAddress  = new InetSocketAddress("localhost", 9183)
}
