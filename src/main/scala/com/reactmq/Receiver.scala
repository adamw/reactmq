package com.reactmq

import akka.io.IO
import akka.stream.io.StreamTcp
import akka.pattern.ask
import akka.stream.scaladsl.Flow
import Framing._
import com.reactmq.queue.MessageData

object Receiver extends App with ReactiveStreamsSupport {

  val connectFuture = IO(StreamTcp) ? StreamTcp.Connect(settings, receiveServerAddress)
  connectFuture.onSuccess {
    case binding: StreamTcp.OutgoingTcpConnection =>
      logger.info("Receiver: connected to broker")

      val reconcileFrames = new ReconcileFrames()

      Flow(binding.inputStream)
        .mapConcat(reconcileFrames.apply)
        .map(MessageData.decodeFromString)
        .map { md =>
          logger.debug(s"Receiver: received msg: $md")
          createFrame(md.id)
        }
        .toProducer(materializer)
        .produceTo(binding.outputStream)
  }

  handleIOFailure(connectFuture, "Receiver: failed to connect to broker")
}
