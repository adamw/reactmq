package com.reactmq

import java.util.concurrent.ThreadLocalRandom

import akka.io.IO
import akka.pattern.ask
import akka.stream.io.StreamTcp
import akka.stream.scaladsl2.{Source, Sink}
import com.reactmq.Framing._

import scala.concurrent.duration._

object Sender extends App with ReactiveStreamsSupport {

  val connectFuture = IO(StreamTcp) ? StreamTcp.Connect(sendServerAddress)
  connectFuture.onSuccess {
    case binding: StreamTcp.OutgoingTcpConnection =>
      logger.info("Sender: connected to broker")

      def nextChar = (ThreadLocalRandom.current().nextInt(91 - 65) + 65).toChar
      val senderName = List.fill(5)(nextChar).mkString
      var idx = 0

      Source(1.second, 1.second, () => { idx += 1; s"Message $idx from $senderName" })
        .map { msg =>
          logger.debug(s"Sender: sending $msg")
          createFrame(msg)
        }
        .connect(Sink(binding.outputStream))
        .run()
  }

  handleIOFailure(connectFuture, "Sender: failed to connect to broker")
}
