package com.reactmq.cluster

import com.reactmq.Receiver

object ClusterReceiver extends App with ClusterClientSupport {
  start("receiver", (ba, system) => new Receiver(ba.receiveServerAddress)(system).run())
}
