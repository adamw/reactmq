package com.reactmq.queue

case class MessageData(id: String, content: String) {
  def encodeAsString = {
    s"$id,$content"
  }
}

object MessageData {
  def decodeFromString(s: String) = {
    val List(id, content) = s.split(",", 2).toList
    MessageData(id, content)
  }
}
