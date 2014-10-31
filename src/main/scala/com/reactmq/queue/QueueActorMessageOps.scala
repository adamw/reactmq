package com.reactmq.queue

import scala.annotation.tailrec
import com.reactmq.Logging
import scala.concurrent.duration._
import com.reactmq.util.NowProvider

/**
 * Copied & simplified from ElasticMQ.
 */
trait QueueActorMessageOps extends Logging {
  this: QueueActorStorage =>

  private val visibilityTimeout = 10.seconds

  def nowProvider: NowProvider

  protected def sendMessage(content: String) = {
    val internalMessage = InternalMessage.from(content)
    messageQueue += internalMessage
    messagesById(internalMessage.id) = internalMessage

    logger.debug(s"Sent message with id ${internalMessage.id}")

    internalMessage
  }

  type ReceiveData = (MessageData, MessageNextDeliveryUpdated)

  protected def receiveMessages(count: Int): List[ReceiveData] = {

    val deliveryTime = nowProvider.nowMillis

    @tailrec
    def doReceiveMessages(left: Int, acc: List[ReceiveData]): List[ReceiveData] = {
      if (left == 0) {
        acc
      } else {
        receiveMessage(deliveryTime, computeNextDelivery) match {
          case None => acc
          case Some(msg) => doReceiveMessages(left - 1, msg :: acc)
        }
      }
    }

    doReceiveMessages(count, Nil)
  }

  @tailrec
  private def receiveMessage(deliveryTime: Long, newNextDelivery: Long): Option[ReceiveData] = {
    if (messageQueue.size == 0) {
      None
    } else {
      val internalMessage = messageQueue.dequeue()
      val id = internalMessage.id

      if (internalMessage.nextDelivery > deliveryTime) {
        // Putting the msg back. That's the youngest msg, so there is no msg that can be received.
        messageQueue += internalMessage
        None
      } else if (messagesById.contains(id)) {
        // Putting the msg again into the queue, with a new next delivery
        internalMessage.nextDelivery = newNextDelivery

        messageQueue += internalMessage

        logger.debug(s"Receiving message $id")

        Some(internalMessage.toMessageData, internalMessage.toMessageNextDeliveryUpdated)
      } else {
        // Deleted msg - trying again
        receiveMessage(deliveryTime, newNextDelivery)
      }
    }
  }

  private def computeNextDelivery = nowProvider.nowMillis + visibilityTimeout.toMillis

  protected def deleteMessage(id: String) {
    messagesById.remove(id).fold(logger.debug(s"Unknown message: $id")) {
      _ => logger.debug(s"Deleted message $id")
    }
  }
}
