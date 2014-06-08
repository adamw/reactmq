package com.reactmq

import akka.io.IO
import akka.stream.io.StreamTcp
import akka.stream.{FlowMaterializer, MaterializerSettings}
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.scaladsl.Flow
import Framing._
import com.reactmq.queue.MessageData

object Receiver extends App with Logging {

  implicit val actorSystem = ActorSystem()
  import actorSystem.dispatcher

  val settings = MaterializerSettings()
  val materializer = FlowMaterializer(settings)

  val connectFuture = IO(StreamTcp) ? StreamTcp.Connect(settings, serverAddress)
  connectFuture.onSuccess {
    case binding: StreamTcp.OutgoingTcpConnection =>
      logger.info("Receiver: connected to broker")

      val reconcileFrames = new ReconcileFrames()

      Flow(binding.inputStream)
        .mapConcat(reconcileFrames.apply)
        .map(MessageData.decodeFromString)
        .map { md =>
          logger.info(s"Receiver: received msg: $md")
          createFrame(md.id)
        }
        .toProducer(materializer)
        .produceTo(binding.outputStream)
  }

  connectFuture.onFailure {
    case e: Throwable =>
      logger.info("Consumer: failed to connect to broker", e)
      actorSystem.shutdown()
  }
}
