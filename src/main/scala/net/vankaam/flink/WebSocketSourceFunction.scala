package net.vankaam.flink


import java.util
import java.util.Collections

import net.vankaam.flink.websocket.{WebSocketClient, WebSocketClientFactory}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.configuration.Configuration

import scala.collection.JavaConversions._
import org.apache.flink.streaming.api.checkpoint.ListCheckpointed
import org.apache.flink.streaming.api.functions.source.{RichSourceFunction, SourceFunction}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._

/**
  *
  * @param url url to the websocket to pull from
  * @param objectName n
  * @param batchSize number of messages requested from the web socket at once
  */
class WebSocketSourceFunction(url: String,objectName: String,batchSize: Int, socketClientFactory: WebSocketClientFactory) extends RichSourceFunction[String] with ListCheckpointed[java.lang.Long] with Serializable {

  /** Time waiting at most for response from the web socket */
  private val patience = 5.seconds

  /** Current position of the reader. Also state of the source */
  private var offset: Long = 0

  /**
    * Buffer storing messages received from the web socket client
    * Needed because we need to implement this source function synchronous, but the web socket api is asynchronous
    */
  private lazy val messageBuffer = mutable.Queue[String]()
  @transient private lazy val webSocketClient = socketClientFactory.getSocket(url, objectName,messageBuffer+=_)


  @volatile private var isRunning = true

  override def run(ctx: SourceFunction.SourceContext[String]): Unit = {
    while(isRunning) {
      Await.ready(webSocketClient.poll(offset,batchSize),patience)
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
    Await.ready(webSocketClient.open(),patience)
    webSocketClient.onClosed.onComplete(_ => cancel())
    super.open(parameters)
  }

  /**
    * Cancels the current job
    */
  override def cancel(): Unit = {
    isRunning = false
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



