package com.reactmq.queue

import akka.actor.{Actor, ActorRef}
import scala.annotation.tailrec

trait QueueActorReceive extends QueueActorMessageOps {
  this: QueueActorStorage with Actor =>

  private val awaitingActors = new collection.mutable.HashMap[ActorRef, Int]()

  def handleQueueMsg: Receive = {
    case sm@SendMessage(content) =>
      sendMessage(content)
      tryReply()
    case rm@ReceiveMessages(count) =>
      addAwaitingActor(rm)
      tryReply()
    case DeleteMessage(id) => deleteMessage(id)
  }

  @tailrec
  private def tryReply() {
    awaitingActors.headOption match {
      case Some((actor, messageCount)) => {
        val received = super.receiveMessages(messageCount)

        if (received != Nil) {
          actor ! received
          logger.debug(s"Replying to $actor with ${received.size} messages.")

          val newMessageCount = messageCount - received.size
          if (newMessageCount > 0) {
            awaitingActors(actor) = newMessageCount
          } else {
            awaitingActors.remove(actor)
            tryReply() // maybe we can send more replies
          }
        }
      }
      case _ => // do nothing
    }
  }

  private def addAwaitingActor(receiveMessages: ReceiveMessages) {
    val actor = sender()
    awaitingActors(actor) = awaitingActors.getOrElse(actor, 0) + receiveMessages.count
  }
}
