package net.vankaam.flink.websocket

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import net.vankaam.testtag.Integration
import org.scalatest.tagobjects.Slow
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.async.Async.{async, await}
import scala.collection.mutable._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise, blocking}
import scala.util.Try


class WebSocketClientSpec extends AsyncFlatSpec with BeforeAndAfterEach with LazyLogging {

  private val config = ConfigFactory.load
  private val url = config.getString("test.websocketclientspec.url")
  private val data = config.getString("test.websocketclientspec.data")

  "WebSocketClient" should "Retrieve and push data from a websocket" taggedAs(Slow,Integration) in async {

    val buffer = new ListBuffer[String]()
    val socket = new WebSocketClient(url,data,buffer+=_)
    await(blocking {socket.open()})
    await(blocking{socket.poll(0,200)})
    await(blocking{socket.poll(200,200)})


    logger.info(s"Closing socket")
    socket.close()
    assert(buffer.size == 400)
  }

  //TODO: Add unit (not integration) tests for the entire behavior
}
