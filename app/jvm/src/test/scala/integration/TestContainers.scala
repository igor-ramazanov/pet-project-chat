package integration
import com.dimafeng.testcontainers.{
  FixedHostPortGenericContainer,
  ForEachTestContainer,
  MultipleContainers
}
import com.github.igorramazanov.chat.UtilsShared._
import org.scalatest.FunSuiteLike
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy

trait TestContainers extends FunSuiteLike with ForEachTestContainer {
  protected lazy val redis =
    FixedHostPortGenericContainer(
      "redis:latest",
      exposedContainerPort = 6379,
      exposedHostPort = 6379
    )

  protected lazy val app: FixedHostPortGenericContainer =
    FixedHostPortGenericContainer(
      imageName = "com.github.igorramazanov/chat:latest",
      exposedContainerPort = 8080,
      exposedHostPort = 8080,
      waitStrategy =
        new LogMessageWaitStrategy().withRegEx("[\\S\\s]*listening[\\S\\s]*")
    ).configure { c =>
      c.withLogConsumer((t: OutputFrame) => print(s">>> ${t.getUtf8String}"))
      c.withCommand(
          "--redis-host",
          redis.containerInfo.getNetworkSettings.getIpAddress
        )
        .discard()
    }

  override val container = MultipleContainers(redis, app)
}
