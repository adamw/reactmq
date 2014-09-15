package com.reactmq

import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.actor.Props
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.pattern.ask
import akka.stream.scaladsl2.FlowFrom
import com.reactmq.queue.{MessageData, DeleteMessage, QueueActor}
import Framing._

object Broker extends App with ReactiveStreamsSupport {

  val ioExt = IO(StreamTcp)
  val bindSendFuture = ioExt ? StreamTcp.Bind(sendServerAddress)
  val bindReceiveFuture = ioExt ? StreamTcp.Bind(receiveServerAddress)

  val queueActor = system.actorOf(Props[QueueActor])

  bindSendFuture.onSuccess {
    case serverBinding: StreamTcp.TcpServerBinding =>
      logger.info("Broker: send bound")

      FlowFrom(serverBinding.connectionStream).map { conn =>
        logger.info(s"Broker: send client connected (${conn.remoteAddress})")

        val sendToQueueSubscriber = ActorSubscriber[String](system.actorOf(Props(new SendToQueueSubscriber(queueActor))))

        // sending messages to the queue, receiving from the client
        val reconcileFrames = new ReconcileFrames()
        FlowFrom(conn.inputStream)
          .mapConcat(reconcileFrames.apply)
          .publishTo(sendToQueueSubscriber)
      }.consume()
  }

  bindReceiveFuture.onSuccess {
    case serverBinding: StreamTcp.TcpServerBinding =>
      logger.info("Broker: receive bound")

      FlowFrom(serverBinding.connectionStream).map { conn =>
        logger.info(s"Broker: receive client connected (${conn.remoteAddress})")

        val receiveFromQueuePublisher = ActorPublisher[MessageData](system.actorOf(Props(new ReceiveFromQueuePublisher(queueActor))))

        // receiving messages from the queue, sending to the client
        FlowFrom(receiveFromQueuePublisher)
          .map(_.encodeAsString)
          .map(createFrame)
          .publishTo(conn.outputStream)

        // replies: ids of messages to delete
        val reconcileFrames = new ReconcileFrames()
        FlowFrom(conn.inputStream)
          .mapConcat(reconcileFrames.apply)
          .map(queueActor ! DeleteMessage(_))
          .consume()
      }.consume()
  }

  handleIOFailure(bindSendFuture, "Broker: failed to bind send endpoint")
  handleIOFailure(bindReceiveFuture, "Broker: failed to bind receive endpoint")
}
