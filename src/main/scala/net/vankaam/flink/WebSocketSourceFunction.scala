package net.vankaam.flink


import java.util
import java.util.Collections

import com.typesafe.scalalogging.LazyLogging
import net.vankaam.websocket.{LoginCookieClient, LoginRequest, WebSocketClient, WebSocketClientFactory}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.configuration.Configuration

import scala.collection.JavaConversions._
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._


object WebSocketSourceFunction {
  /**
    * Builder which fills in the websocket client factory
    * @param url URL to which the websocket should connect
    * @param objectName objectName the client should request
    * @param batchSize number of items that should be buffered outside of checkpoint lock
    * @return
    */
  def apply(url: String, objectName: String, batchSize: Int): WebSocketSourceFunction = new WebSocketSourceFunction(url, objectName, batchSize,WebSocketClientFactory,None)

  /**
    * Construct a websocket with login functionality using a typed configuration
    * @param config the configuration object
    * @return
    */
  def apply(config:WebSocketClientConfig): WebSocketSourceFunction = new WebSocketSourceFunction(config.wssUri,config.subject,config.batchSize,WebSocketClientFactory,Some(WebSocketLoginContext(config.loginUri,config.userName,config.password)))
}

case class WebSocketLoginContext(uri: String, userName: String, password: String)



/**
  *
  * @param url url to the websocket to pull from
  * @param objectName name of the subject to request from the websocket
  * @param batchSize number of messages requested from the web socket at once
  */
class WebSocketSourceFunction(url: String,objectName: String,batchSize: Int,webSocketClientFactory:WebSocketClientFactory, loginContext: Option[WebSocketLoginContext]) extends RichSourceFunction[String] with ListCheckpointed[java.lang.Long] with Serializable with LazyLogging {

  /** Time waiting at most for response from the web socket */
  private val patience = 60.seconds

  /** Current position of the reader. Also state of the source */
  private var offset: Long = 0

  private val loginClient = loginContext.map(c => {
    new LoginCookieClient(c.uri,LoginRequest(c.userName,c.password))
  })

  /**
    * Buffer storing messages received from the web socket client
    * Needed because we need to implement this source function synchronous, but the web socket api is asynchronous
    */
  private lazy val messageBuffer = mutable.Queue[String]()
  @transient private lazy val webSocketClient = webSocketClientFactory.getSocket(url, objectName,messageBuffer+=_,loginClient)



  @volatile private var isRunning = true

  override def run(ctx: SourceFunction.SourceContext[String]): Unit = {
    while(isRunning) {
      isRunning = Await.result(webSocketClient.poll(offset,batchSize),patience)
      ctx.getCheckpointLock.synchronized {
        while (messageBuffer.nonEmpty) {
          ctx.collect(messageBuffer.dequeue())
          offset += 1
        }
      }
    }

    cleanUp()
  }

  /**
    * Open the connection to the web socket
    * @param parameters configuration parameters. Currently unused.
    */
  override def open(parameters: Configuration): Unit = {
    Await.result(webSocketClient.open(),patience)
    webSocketClient.onClosed.onComplete(_ => cancel())
    logger.info("Web socket opened")
    super.open(parameters)
  }

  /**
    * Cancels the current job
    */
  override def cancel(): Unit = {
    webSocketClient.close()
  }


  /** Perform cleanup after run has finished */
  private def cleanUp(): Unit = {
    webSocketClient.close()
  }

  /** Restore offset to given point */
  override def restoreState(state: util.List[java.lang.Long]): Unit = {
    for(s <- state) {
      offset = s
    }
  }

  /** Snapshot current offset */
  override def snapshotState(checkpointId: Long, timestamp: Long): util.List[java.lang.Long] =
    Collections.singletonList(offset)
}



