package com.reactmq.queue

import akka.actor.Actor
import com.reactmq.util.NowProvider

class QueueActor extends Actor with QueueActorStorage with QueueActorReceive {
  val nowProvider = new NowProvider()

  override def receive = handleQueueMsg
}

