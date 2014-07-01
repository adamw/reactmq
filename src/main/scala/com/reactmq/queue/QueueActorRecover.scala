package com.reactmq.queue

import akka.actor.Actor.Receive
import akka.persistence.RecoveryCompleted
import com.reactmq.Logging

trait QueueActorRecover extends Logging {
  this: QueueActorStorage =>

  def handleQueueEvent: Receive = {
    case MessageAdded(id, nextDelivery, content) => messagesById(id) = InternalMessage(id, nextDelivery, content)
    case MessageNextDeliveryUpdated(id, nextDelivery) => messagesById.get(id).foreach(_.nextDelivery = nextDelivery)
    case MessageDeleted(id) => messagesById.remove(id)

    case RecoveryCompleted =>
      messageQueue ++= messagesById.values
      logger.info(s"Recovered ${messagesById.size} messages.")
  }
}
