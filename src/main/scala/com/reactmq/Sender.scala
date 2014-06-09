package com.reactmq

import akka.io.IO
import akka.stream.io.StreamTcp
import akka.pattern.ask
import akka.stream.scaladsl.Flow
import Framing._
import scala.concurrent.duration._
import java.util.concurrent.ThreadLocalRandom

object Sender extends App with ReactiveStreamsSupport {

  val connectFuture = IO(StreamTcp) ? StreamTcp.Connect(settings, sendServerAddress)
  connectFuture.onSuccess {
    case binding: StreamTcp.OutgoingTcpConnection =>
      logger.info("Sender: connected to broker")

      def nextChar = (ThreadLocalRandom.current().nextInt(91 - 65) + 65).toChar
      val senderName = List.fill(5)(nextChar).mkString
      var idx = 0

      Flow(binding.inputStream)
        .foreach(println)
        .consume(materializer)

      Flow(1.second, () => { idx += 1; s"Message $idx from $senderName" })
        .map { msg =>
          logger.debug(s"Sender: sending $msg")
          createFrame(msg)
        }
        .toProducer(materializer)
        .produceTo(binding.outputStream)
  }

  handleIOFailure(connectFuture, "Sender: failed to connect to broker")
}
