package net.vankaam.flink

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
/**
  * A sample Flink job that uses the web socket source function
  *
  * The websocket server should first accept a text message with the subject
  * After the subject this client will ask for a number of messages and offset.
  * For example "10.0" should trigger the server to send 10 messages.
  * The client will increment offset by the number of messages recieved. Only on failure the client will ask for the same offsets again
  *
  * Usage:
  * {{{
  *   WebSocketSample --url ws://sample.net:8900 --subject mySubject
  * }}}
  */
object WebSocketSample extends LazyLogging {
    def main(args: Array[String]): Unit = {

      val params: ParameterTool = ParameterTool.fromArgs(args)
      val env = StreamExecutionEnvironment.getExecutionEnvironment

      //The source is not natively built for parallelism, although most complexity would be at the producer side
      env.setParallelism(1)

      val url = params.getRequired("url")
      val subject = params.getRequired("subject")
      val batchSize = 10

      //Create the source
      val source = WebSocketSourceFunction(url,subject,batchSize)

      //Print results in console
      env.addSource(source).addSink(logger.info(_))

      //And go
      env.execute()
    }
}
