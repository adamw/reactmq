package com.reactmq

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.pattern.ask
import Framing._
import akka.stream.scaladsl2.{Source, Sink}
import com.reactmq.queue.MessageData

class Receiver(receiveServerAddress: InetSocketAddress)(implicit val system: ActorSystem) extends ReactiveStreamsSupport {
  def run() {
    val connectFuture = IO(StreamTcp) ? StreamTcp.Connect(receiveServerAddress)
    connectFuture.onSuccess {
      case binding: StreamTcp.OutgoingTcpConnection =>
        logger.info("Receiver: connected to broker")

        val reconcileFrames = new ReconcileFrames()

        Source(binding.inputStream)
          .mapConcat(reconcileFrames.apply)
          .map(MessageData.decodeFromString)
          .map { md =>
            logger.debug(s"Receiver: received msg: $md")
            createFrame(md.id)
          }
          .connect(Sink(binding.outputStream))
          .run()
    }

    handleIOFailure(connectFuture, "Receiver: failed to connect to broker")
  }
}

object SimpleReceiver extends App with SimpleServerSupport {
  new Receiver(receiveServerAddress).run()
}