package com.reactmq

import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.{Promise, Future}

trait ReactiveStreamsSupport extends Logging {
  implicit def system: ActorSystem

  implicit val dispatcher = system.dispatcher

  implicit val timeout = Timeout(5.seconds)

  implicit val materializer = FlowMaterializer()

  def handleIOFailure(ioFuture: Future[Any], msg: => String, failPromise: Option[Promise[Unit]] = None) {
    ioFuture.onFailure {
      case e: Exception =>
        logger.error(msg, e)
        failPromise.foreach(_.failure(e))
    }
  }
}
