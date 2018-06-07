package net.vankaam.flink

import com.typesafe.config.Config

case class WebSocketClientConfig(subject:String,wssUri: String, loginUri:String, userName: String, password: String,batchSize:Int)

object WebSocketClientConfig {
  def apply(c:Config): WebSocketClientConfig =
    WebSocketClientConfig(
      c.getString("subject"),
      c.getString("wssUri"),
      c.getString("loginUri"),
      c.getString("userName"),
      c.getString("password"),
      c.getInt("batchSize"))
}