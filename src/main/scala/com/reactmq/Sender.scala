package com.reactmq

import java.net.InetSocketAddress
import java.util.concurrent.ThreadLocalRandom

import akka.actor.ActorSystem
import akka.stream.io.StreamTcp
import akka.stream.scaladsl._
import akka.util.ByteString
import com.reactmq.Framing._

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._

class Sender(sendServerAddress: InetSocketAddress)(implicit val system: ActorSystem) extends ReactiveStreamsSupport {
  def run(): Future[Unit] = {
    val completionPromise = Promise[Unit]()

    val serverConnection = StreamTcp().outgoingConnection(sendServerAddress)

    def nextChar = (ThreadLocalRandom.current().nextInt(91 - 65) + 65).toChar
    val senderName = List.fill(5)(nextChar).mkString
    var idx = 0

    val source = Source(1.second, 1.second, () => { idx += 1; s"Message $idx from $senderName" })
      .map { msg =>
      logger.debug(s"Sender: sending $msg")
      createFrame(msg)
    }
    val sink = OnCompleteSink[ByteString] { t => completionPromise.complete(t); () }

    val materializedMap = source.via(serverConnection.flow).to(sink).run()

    val connectFuture = serverConnection.localAddress(materializedMap)
    connectFuture.onSuccess { case _ => logger.debug(s"Sender: connected to broker") }
    connectFuture.onFailure { case e: Exception => logger.error("Sender: failed to connect to broker", e) }

    completionPromise.future
  }
}

object SimpleSender extends App with SimpleServerSupport with Logging {
  val sender: Sender = new Sender(sendServerAddress)
  import sender.system.dispatcher
  sender.run().onComplete(result => logger.info("Sender: completed with result " + result))
}
