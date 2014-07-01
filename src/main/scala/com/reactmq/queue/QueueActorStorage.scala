package com.reactmq.queue

import scala.collection.mutable
import java.util.UUID
import com.reactmq.util.NowProvider

/**
 * Copied & simplified from ElasticMQ.
 */
trait QueueActorStorage {
  def nowProvider: NowProvider

  var messageQueue = mutable.PriorityQueue[InternalMessage]()
  val messagesById = mutable.HashMap[String, InternalMessage]()

  case class InternalMessage(
    id: String,
    var nextDelivery: Long,
    content: String) extends Comparable[InternalMessage] {

    // Priority queues have biggest elements first
    def compareTo(other: InternalMessage) = - nextDelivery.compareTo(other.nextDelivery)

    def toMessageData = MessageData(id, content)

    def toMessageAdded = MessageAdded(id, nextDelivery, content)
    def toMessageNextDeliveryUpdated = MessageNextDeliveryUpdated(id, nextDelivery)
  }

  object InternalMessage {
    def from(content: String) = InternalMessage(
      UUID.randomUUID().toString,
      nowProvider.nowMillis,
      content)
  }
}
