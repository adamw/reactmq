package com.reactmq

import akka.stream.scaladsl.Flow
import akka.actor.Props
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.pattern.ask
import com.reactmq.queue.{MessageData, SendMessage, DeleteMessage, QueueActor}
import scala.concurrent.duration._
import akka.stream.actor.ActorProducer
import Framing._

object Broker extends App with Logging with ReactiveStreamsSupport {

  val bindFuture = IO(StreamTcp) ? StreamTcp.Bind(settings, receiveServerAddress)

  val queueActor = system.actorOf(Props[QueueActor])
  var idx = 0
  system.scheduler.schedule(0.seconds, 1.second, queueActor, SendMessage({ idx += 1; s"Message $idx" }))

  bindFuture.onSuccess {
    case serverBinding: StreamTcp.TcpServerBinding =>
      logger.info("Broker: bound")

      Flow(serverBinding.connectionStream).foreach { conn =>
        logger.info(s"Broker: client connected (${conn.remoteAddress})")

        val receiveFromQueueProducer = ActorProducer[MessageData](system.actorOf(Props(new ReceiveFromQueueProducer(queueActor))))

        // sending messages
        Flow(receiveFromQueueProducer)
          .map(_.encodeAsString)
          .map(createFrame)
          .toProducer(materializer)
          .produceTo(conn.outputStream)

        // replies: ids of messages to delete
        val reconcileFrames = new ReconcileFrames()
        Flow(conn.inputStream)
          .mapConcat(reconcileFrames.apply)
          .foreach(queueActor ! DeleteMessage(_))
          .consume(materializer)
      }.consume(materializer)
  }

  bindFuture.onFailure {
    case e: Throwable =>
      logger.info("Broker: failed to bind", e)
      system.shutdown()
  }
}
