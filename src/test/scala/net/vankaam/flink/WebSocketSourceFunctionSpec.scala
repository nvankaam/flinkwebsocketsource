package net.vankaam.flink


import net.vankaam.websocket.{WebSocketClient, WebSocketClientFactory}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterEach}

import scala.async.Async.{async, await}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

/**
  * Tests for WebSocketSourceFunction
  */
class WebSocketSourceFunctionSpec  extends AsyncFlatSpec with BeforeAndAfterEach with MockitoSugar {

  private var webSocketClient:WebSocketClient = _
  private var webSocketClientFactory:WebSocketClientFactory = _
  private var callBack: String => Unit = _
  private var ctx: SourceFunction.SourceContext[String] = _
  private val checkpointLock:Object = new Object()

  private var completionPromise: Promise[Unit] = _
  private val testUrl = "ws://sample.com:8900"
  private val testObject = "sampleObject"
  private val testBatchSize = 3


  "Open" should "Open the connection" in {
    //Arrange
    val component = getComponent

    //Act
    component.open(mock[Configuration])

    //Assert
    verify(webSocketClient,times(1)).open()
    assert(true) //Verify is no assertion according to asyncFlatSpec
  }

  it should "Construct the websocket with configured parameters" in {
    //Arrange
    val component = getComponent

    //Act
    component.open(mock[Configuration])

    //Assert
    verify(webSocketClientFactory, times(1)).getSocket(ArgumentMatchers.eq(testUrl),ArgumentMatchers.eq(testObject),ArgumentMatchers.any())
    assert(true) //Verify is no assertion according to asyncFlatSpec
  }

  "Run" should "Collect exposed data" in async {
    //Arrange
    val component = getOpenComponent
    val pollCompletionPromise = Promise[Boolean]()

    when(webSocketClient.poll(0,3)) thenReturn pollCompletionPromise.future
    //Act
    val f =Future {
      component.run(ctx)
    }
    callBack("a")
    callBack("b")
    callBack("c")


    completionPromise.success(())
    pollCompletionPromise.success(false)
    await(f)

    //Assert
    verify(ctx,times(1)).collect("a")
    verify(ctx,times(1)).collect("b")
    verify(ctx,times(1)).collect("c")
    assert(true) //Verify is no assertion according to asyncFlatSpec
  }

  "SnapShotState" should "Return the current offset as state" in async {
    val component = getOpenComponent
    val pollCompletionPromise = Promise[Boolean]()

    when(webSocketClient.poll(0,3)) thenReturn pollCompletionPromise.future
    //Act
    val before = component.snapshotState(0,0)
    val f =Future {
      component.run(ctx)
    }
    callBack("a")
    callBack("b")
    callBack("c")
    completionPromise.success(())
    pollCompletionPromise.success(false)
    await(f)
    val after = component.snapshotState(1,0)


    //Assert
    assert(before.size() == 1)
    assert(before.get(0) == 0)

    assert(after.size() == 1)
    assert(after.get(0) == 3)
  }

  "RestoreState" should "set the offset" in {
    //Arrange
    val component = getOpenComponent

    //Act
    component.restoreState(List[java.lang.Long](1337L).asJava)

    //Assert
    assert(component.snapshotState(0,0).get(0) == 1337)

  }

  private def getComponent: WebSocketSourceFunction = new WebSocketSourceFunction(testUrl,testObject,testBatchSize,webSocketClientFactory,None)

  private def getOpenComponent: WebSocketSourceFunction = {
    val component =getComponent
    component.open(mock[Configuration])
    component
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    completionPromise = Promise[Unit]()
    webSocketClient = mock[WebSocketClient]
    when(webSocketClient.open()).thenReturn(Future.successful(()))
    when(webSocketClient.onClosed) thenReturn completionPromise.future

    ctx = mock[SourceFunction.SourceContext[String]]
    when(ctx.getCheckpointLock).thenReturn(checkpointLock,checkpointLock)

    webSocketClientFactory = mock[WebSocketClientFactory]
    when(webSocketClientFactory.getSocket(ArgumentMatchers.any(),ArgumentMatchers.any(),ArgumentMatchers.any())).thenAnswer((o: InvocationOnMock) => {
      callBack = o.getArgument[String => Unit](2)
      webSocketClient
    })
  }


  implicit def toAnswerWithArguments[T](f: InvocationOnMock => T): Answer[T] = new Answer[T] {
    override def answer(invocation: InvocationOnMock): T = f(invocation)
  }

  implicit def toAnswer[T](f: () => T): Answer[T] = new Answer[T] {
    override def answer(invocation: InvocationOnMock): T = f()
  }
}
