package com.reactmq

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.pattern.ask
import Framing._
import akka.stream.scaladsl2._
import FlowGraphImplicits._
import akka.util.ByteString
import com.reactmq.queue.MessageData

import scala.concurrent.{Future, Promise}

class Receiver(receiveServerAddress: InetSocketAddress)(implicit val system: ActorSystem) extends ReactiveStreamsSupport {
  def run(): Future[Unit] = {
    val completionPromise = Promise[Unit]()

    val connectFuture = IO(StreamTcp) ? StreamTcp.Connect(receiveServerAddress)
    connectFuture.onSuccess {
      case binding: StreamTcp.OutgoingTcpConnection =>
        logger.info("Receiver: connected to broker")

        val reconcileFrames = new ReconcileFrames()

        FlowGraph { implicit b =>
          val split = Broadcast[ByteString]
          Source(binding.inputStream) ~> split

          val mainFlow = Flow[ByteString]
            .mapConcat(reconcileFrames.apply)
            .map(MessageData.decodeFromString)
            .map { md =>
              logger.debug(s"Receiver: received msg: $md")
              createFrame(md.id)
            }

          split ~> mainFlow ~> Sink(binding.outputStream)
          split ~> OnCompleteDrain[ByteString] { t => completionPromise.complete(t); () }
        }.run()
    }

    handleIOFailure(connectFuture, "Receiver: failed to connect to broker", Some(completionPromise))

    completionPromise.future
  }
}

object SimpleReceiver extends App with SimpleServerSupport {
  new Receiver(receiveServerAddress).run()
}