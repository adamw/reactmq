package com.reactmq.queue

import akka.actor.{Actor, ActorRef}
import scala.annotation.tailrec

trait QueueActorReceive extends QueueActorMessageOps {
  this: QueueActorStorage with Actor =>

  private var senderSequence = 0L
  private val awaitingActors = new collection.mutable.HashMap[Long, ActorAwaitingForMessages]()

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
      case Some((seq, aa@ActorAwaitingForMessages(actor, messageCount))) => {
        val received = super.receiveMessages(messageCount)

        if (received != Nil) {
          actor ! received
          logger.debug(s"Replying to sequence $seq with ${received.size} messages.")

          val newAwaitingActor = aa.subtractMessages(received.size)
          if (newAwaitingActor.hasMessages) {
            awaitingActors(seq) = newAwaitingActor
          } else {
            awaitingActors.remove(seq)
            tryReply() // maybe we can send more replies
          }
        }
      }
      case _ => // do nothing
    }
  }

  private def addAwaitingActor(receiveMessages: ReceiveMessages): Long = {
    val seq = senderSequence
    senderSequence += 1
    awaitingActors(seq) = ActorAwaitingForMessages(sender(), receiveMessages.count)
    seq
  }

  case class ActorAwaitingForMessages(actor: ActorRef, messageCount: Int) {
    def hasMessages = messageCount != 0
    def subtractMessages(count: Int) = this.copy(messageCount = messageCount - count)
  }
}
