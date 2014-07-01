package com.reactmq.queue

import akka.persistence.PersistentActor
import com.reactmq.util.NowProvider

class QueueActor extends PersistentActor with QueueActorStorage with QueueActorReceive with QueueActorRecover {
  override def persistenceId = "queue-actor"

  val nowProvider = new NowProvider()

  def receiveCommand = handleQueueMsg

  def receiveRecover = handleQueueEvent
}

