package com.reactmq

import akka.stream.scaladsl.Flow
import akka.actor.Props
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.pattern.ask
import com.reactmq.queue.{MessageData, DeleteMessage, QueueActor}
import akka.stream.actor.{ActorConsumer, ActorProducer}
import Framing._

object Broker extends App with ReactiveStreamsSupport {

  val ioExt = IO(StreamTcp)
  val bindSendFuture = ioExt ? StreamTcp.Bind(settings, sendServerAddress)
  val bindReceiveFuture = ioExt ? StreamTcp.Bind(settings, receiveServerAddress)

  val queueActor = system.actorOf(Props[QueueActor])

  bindSendFuture.onSuccess {
    case serverBinding: StreamTcp.TcpServerBinding =>
      logger.info("Broker: send bound")

      Flow(serverBinding.connectionStream).foreach { conn =>
        logger.info(s"Broker: send client connected (${conn.remoteAddress})")

        val sendToQueueConsumer = ActorConsumer[String](system.actorOf(Props(new SendToQueueConsumer(queueActor))))

        // sending messages to the queue, receiving from the client
        val reconcileFrames = new ReconcileFrames()
        Flow(conn.inputStream)
          .mapConcat(reconcileFrames.apply)
          .produceTo(materializer, sendToQueueConsumer)
      }.consume(materializer)
  }

  bindReceiveFuture.onSuccess {
    case serverBinding: StreamTcp.TcpServerBinding =>
      logger.info("Broker: receive bound")

      Flow(serverBinding.connectionStream).foreach { conn =>
        logger.info(s"Broker: receive client connected (${conn.remoteAddress})")

        val receiveFromQueueProducer = ActorProducer[MessageData](system.actorOf(Props(new ReceiveFromQueueProducer(queueActor))))

        // receiving messages from the queue, sending to the client
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

  handleIOFailure(bindSendFuture, "Broker: failed to bind send endpoint")
  handleIOFailure(bindReceiveFuture, "Broker: failed to bind receive endpoint")
}
