package com.reactmq.cluster

import java.net.InetSocketAddress

import akka.actor.{Actor, Props, PoisonPill, ActorSystem}
import akka.contrib.pattern.{ClusterReceptionistExtension, ClusterSingletonManager}
import com.reactmq.Broker
import com.typesafe.config.ConfigFactory

/*
 * i = 1, 2, 3 - the broker id
 * Cluster port:  9170 + i
 * Sender port:   9180 + i
 * Receiver port: 9190 + i
 */
class BrokerManager(clusterPort: Int) {
  def run(): Unit = {
    val conf = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$clusterPort")
      .withFallback(ConfigFactory.load("cluster-broker-template"))

    val system = ActorSystem(s"broker", conf)

    system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(classOf[BrokerManagerActor], clusterPort),
      singletonName = "broker",
      terminationMessage = PoisonPill,
      role = Some("broker")),
      name = "broker-manager")
  }
}

class BrokerManagerActor(clusterPort: Int) extends Actor {
  val sendServerAddress     = new InetSocketAddress("localhost", clusterPort + 10)
  val receiveServerAddress  = new InetSocketAddress("localhost", clusterPort + 20)

  override def preStart() = {
    super.preStart()
    new Broker(sendServerAddress, receiveServerAddress)(context.system).run()
    ClusterReceptionistExtension(context.system).registerService(self)
  }

  override def receive = {
    case GetBrokerAddresses => sender() ! BrokerAddresses(sendServerAddress, receiveServerAddress)
  }
}

case object GetBrokerAddresses
case class BrokerAddresses(sendServerAddress: InetSocketAddress, receiveServerAddress: InetSocketAddress)

object ClusteredBroker1 extends App {
  new BrokerManager(9171).run()
}

object ClusteredBroker2 extends App {
  new BrokerManager(9172).run()
}

object ClusteredBroker3 extends App {
  new BrokerManager(9173).run()
}