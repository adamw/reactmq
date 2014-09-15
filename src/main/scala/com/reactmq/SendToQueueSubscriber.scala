package com.reactmq

import akka.actor.ActorRef
import akka.stream.actor.{MaxInFlightRequestStrategy, ActorSubscriber}
import com.reactmq.queue.{SendMessage, SentMessage}
import akka.stream.actor.ActorSubscriberMessage.OnNext

class SendToQueueSubscriber(queueActor: ActorRef) extends ActorSubscriber {

  private var inFlight = 0

  override protected def requestStrategy = new MaxInFlightRequestStrategy(10) {
    override def inFlightInternally = inFlight
  }

  override def receive = {
    case OnNext(msg: String) =>
      queueActor ! SendMessage(msg)
      inFlight += 1

    case SentMessage(_) => inFlight -= 1
  }
}
