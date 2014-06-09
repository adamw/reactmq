package com.reactmq.queue

import akka.actor.{Actor, ActorRef}
import scala.annotation.tailrec

trait QueueActorReceive extends QueueActorMessageOps {
  this: QueueActorStorage with Actor =>

  private val awaitingActors = new collection.mutable.HashMap[ActorRef, Int]()

  def handleQueueMsg: Receive = {
    case SendMessage(content) =>
      val id = sendMessage(content)
      sender() ! SentMessage(id)
      tryReply()

    case ReceiveMessages(count) =>
      addAwaitingActor(sender(), count)
      tryReply()

    case DeleteMessage(id) =>
      deleteMessage(id)
  }

  @tailrec
  private def tryReply() {
    awaitingActors.headOption match {
      case Some((actor, messageCount)) => {
        val received = super.receiveMessages(messageCount)

        if (received != Nil) {
          actor ! ReceivedMessages(received)
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

  private def addAwaitingActor(actor: ActorRef, count: Int) {
    awaitingActors(actor) = awaitingActors.getOrElse(actor, 0) + count
  }
}
