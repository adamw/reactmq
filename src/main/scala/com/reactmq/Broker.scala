package com.reactmq

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props}
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.io.StreamTcp
import akka.stream.scaladsl._
import akka.util.ByteString
import com.reactmq.Framing._
import com.reactmq.queue.{DeleteMessage, MessageData, QueueActor}

import scala.concurrent.{Promise, Future}

class Broker(sendServerAddress: InetSocketAddress, receiveServerAddress: InetSocketAddress)
            (implicit val system: ActorSystem) extends ReactiveStreamsSupport {

  def run(): Unit = {
    val queueActor = system.actorOf(Props[QueueActor])

    val sendFuture = StreamTcp().bind(sendServerAddress).connections.foreach { conn =>
      logger.info(s"Broker: send client connected (${conn.remoteAddress})")

      val sendToQueueSubscriber = ActorSubscriber[String](system.actorOf(Props(new SendToQueueSubscriber(queueActor))))

      // sending messages to the queue, receiving from the client
      val reconcileFrames = new ReconcileFrames()

      val sendSink = Flow[ByteString]
        .mapConcat(reconcileFrames.apply)
        .to(Sink(sendToQueueSubscriber))

      // TODO?
      conn.flow.to(sendSink).runWith(FutureSource(Promise().future))
    }

    val receiveFuture = StreamTcp().bind(receiveServerAddress).connections.foreach { conn =>
      logger.info(s"Broker: receive client connected (${conn.remoteAddress})")

      val receiveFromQueuePublisher = ActorPublisher[MessageData](system.actorOf(Props(new ReceiveFromQueuePublisher(queueActor))))

      // receiving messages from the queue, sending to the client
      val receiveSource = Source(receiveFromQueuePublisher)
        .map(_.encodeAsString)
        .map(createFrame)

      // replies: ids of messages to delete
      val reconcileFrames = new ReconcileFrames()
      val replySink = Flow[ByteString]
        .mapConcat(reconcileFrames.apply)
        .map(queueActor ! DeleteMessage(_))
        .to(BlackholeSink)

      receiveSource.via(conn.flow).to(replySink).run()
    }

    handleIOFailure(sendFuture, "Broker: failed to bind send endpoint")
    handleIOFailure(receiveFuture, "Broker: failed to bind receive endpoint")
  }

  private def handleIOFailure(ioFuture: Future[Any], msg: => String, failPromise: Option[Promise[Unit]] = None) {
    ioFuture.onFailure {
      case e: Exception =>
        logger.error(msg, e)
        failPromise.foreach(_.failure(e))
    }
  }
}

object SimpleBroker extends App with SimpleServerSupport {
  new Broker(sendServerAddress, receiveServerAddress).run()
}