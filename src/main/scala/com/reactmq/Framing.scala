package com.reactmq

import akka.util.ByteString
import javax.annotation.concurrent.NotThreadSafe
import java.nio.ByteBuffer
import java.nio.charset.Charset

object Framing {
  val SizeBytes = 4
  private val utf8charset = Charset.forName("UTF-8")

  def createFrame(content: String): ByteString = {
    val contentBytes = content.getBytes(utf8charset)

    val bb = ByteBuffer
      .allocate(SizeBytes + contentBytes.length)
      .putInt(contentBytes.length)
      .put(contentBytes)

    bb.flip()

    ByteString(bb)
  }
}

@NotThreadSafe
class ReconcileFrames() {
  import Framing.SizeBytes

  private var buffer = ByteString()
  private var nextContentSize: Option[Int] = None

  def apply(fragment: ByteString): List[String] = {
    buffer = buffer ++ fragment
    tryReadContents()
  }

  private def tryReadContents(): List[String] = {
    nextContentSize match {
      case Some(contentSize) =>
        tryReadContentsWithNextSize(contentSize)
      case None =>
        if (buffer.size >= SizeBytes) {
          val contentSize = buffer.take(SizeBytes).toByteBuffer.getInt
          nextContentSize = Some(contentSize)
          tryReadContentsWithNextSize(contentSize)
        } else {
          Nil
        }
    }
  }

  private def tryReadContentsWithNextSize(contentSize: Int): List[String] = {
    if (buffer.size >= SizeBytes + contentSize) {
      val content = buffer.slice(SizeBytes, SizeBytes + contentSize).utf8String
      buffer = buffer.drop(SizeBytes + contentSize)
      nextContentSize = None
      content :: tryReadContents()
    } else {
      Nil
    }
  }
}