package com.reactmq.cluster

import com.reactmq.Sender

object ClusterSender extends App with ClusterClientSupport {
  start("sender", (ba, system) => new Sender(ba.sendServerAddress)(system).run())
}
