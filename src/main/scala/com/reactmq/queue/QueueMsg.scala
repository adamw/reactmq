package com.reactmq.queue

case class SendMessage(content: String)
case class ReceiveMessages(count: Int)
case class DeleteMessage(id: String)

// replies
case class SentMessage(id: String)
case class ReceivedMessages(msgs: List[MessageData])