package com.reactmq

import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.util.Timeout

import scala.concurrent.duration._

trait ReactiveStreamsSupport extends Logging {
  implicit def system: ActorSystem

  implicit val dispatcher = system.dispatcher

  implicit val timeout = Timeout(5.seconds)

  implicit val materializer = FlowMaterializer()
}
