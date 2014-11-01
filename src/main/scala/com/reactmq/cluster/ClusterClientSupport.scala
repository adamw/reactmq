package com.reactmq.cluster

import akka.actor.{ActorSystem, AddressFromURIString, RootActorPath}
import akka.contrib.pattern.ClusterClient
import akka.pattern.ask
import akka.util.Timeout
import com.reactmq.Logging
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

trait ClusterClientSupport extends Logging {
  def start(name: String, runClient: (BrokerAddresses, ActorSystem) => Future[Unit]) {
    val conf = ConfigFactory.load("cluster-client")
    implicit val system = ActorSystem(name, conf)
    import system.dispatcher

    val initialContacts = conf.getStringList("cluster.client.initial-contact-points").asScala.map {
      case AddressFromURIString(addr) => system.actorSelection(RootActorPath(addr) / "user" / "receptionist")
    }.toSet
    logger.info(s"Initial cluster contact: $initialContacts")

    val clusterClient = system.actorOf(ClusterClient.props(initialContacts), "cluster-client")

    def go(): Unit = {
      implicit val timeout = Timeout(10.seconds)
      val completionFuture = (clusterClient ? ClusterClient.Send("/user/broker-manager/broker", GetBrokerAddresses, localAffinity = false))
        .mapTo[BrokerAddresses]
        .flatMap { ba =>
          logger.info(s"Connecting a $name using broker address $ba.")
          runClient(ba, system)
        }

      completionFuture.onComplete { result =>
        logger.info(s"$name completed with result $result. Scheduling restart after 1 second.")
        system.scheduler.scheduleOnce(1.second, new Runnable {
          override def run() = go()
        })
      }
    }

    go()
  }
}
