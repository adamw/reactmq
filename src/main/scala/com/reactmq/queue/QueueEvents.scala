package com.reactmq.queue

case class MessageAdded(id: String, nextDelivery: Long, content: String)
case class MessageNextDeliveryUpdated(id: String, nextDelivery: Long)
case class MessageDeleted(id: String)
