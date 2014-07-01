package com.reactmq.queue

import akka.actor.ActorRef
import akka.persistence.PersistentActor

import scala.annotation.tailrec

trait QueueActorReceive extends QueueActorMessageOps {
  this: QueueActorStorage with PersistentActor =>

  private val awaitingActors = new collection.mutable.HashMap[ActorRef, Int]()

  def handleQueueMsg: Receive = {
    case SendMessage(content) =>
      val msg = sendMessage(content)
      persistAsync(msg.toMessageAdded) { msgAdded =>
        sender() ! SentMessage(msgAdded.id)
        tryReply()
      }

    case ReceiveMessages(count) =>
      addAwaitingActor(sender(), count)
      tryReply()

    case DeleteMessage(id) =>
      deleteMessage(id)
      persistAsync(MessageDeleted(id)) { _ => }
  }

  @tailrec
  private def tryReply() {
    awaitingActors.headOption match {
      case Some((actor, messageCount)) =>
        val received = receiveMessages(messageCount)
        persistAsync(received.map(_._2)) { _ => }

        if (received != Nil) {
          actor ! ReceivedMessages(received.map(_._1))
          logger.debug(s"Replying to $actor with ${received.size} messages.")

          val newMessageCount = messageCount - received.size
          if (newMessageCount > 0) {
            awaitingActors(actor) = newMessageCount
          } else {
            awaitingActors.remove(actor)
            tryReply() // maybe we can send more replies
          }
        }
      case _ => // do nothing
    }
  }

  private def addAwaitingActor(actor: ActorRef, count: Int) {
    awaitingActors(actor) = awaitingActors.getOrElse(actor, 0) + count
  }
}
