package integration
import com.github.igorramazanov.chat.ResponseCode
import com.github.igorramazanov.chat.UtilsShared._
import com.github.igorramazanov.chat.domain.ChatMessage
import org.scalacheck.Gen
import org.scalatest.Matchers._
import util.DomainEntitiesGenerators

class ITest
    extends ITestHarness
    with DomainEntitiesGenerators
    with TestContainers {
  test(
    "it should response with 'InvalidCredentials' if /signin for unexistent account"
  ) {
    forAll(userGen) { user =>
      val (status, _) = signIn(user)
      status shouldBe ResponseCode.InvalidCredentials.value
    }
  }

  test(
    "it should response with 'ValidationErrors' if /signin with invalid credentials"
  ) {
    forAll(invalidIdGen, invalidEmailGen, invalidPasswordGen) {
      case ((invalidId, _), (invalidEmail, _), (invalidPassword, _)) =>
        val (status, _) =
          signIn(invalidId, invalidEmail, invalidPassword)
        status shouldBe ResponseCode.ValidationErrors.value
    }
  }

  test("it should response with 'Ok' if /signup with valid credentials") {
    forAll(userGen) { user =>
      val status = signUp(user)
      status shouldBe ResponseCode.Ok.value
    }
  }

  test(
    "it should response with 'ValidationErrors' if /signup with invalid credentials"
  ) {
    forAll(invalidIdGen, invalidEmailGen, invalidPasswordGen) {
      case ((invalidId, _), (invalidEmail, _), (invalidPassword, _)) =>
        val status =
          signUp(invalidId, invalidEmail, invalidPassword)
        status shouldBe ResponseCode.ValidationErrors.value
    }
  }

  test("users should be able send messages to each other") {
    forAll(userGen, userGen, Gen.listOf(Gen.alphaNumStr)) {
      (userA, userB, payloads) =>
        whenever(userA.id !== userB.id) {
          signUp(userA)
          signUp(userB)
          val outgoingMessages =
            payloads.map(ChatMessage.IncomingChatMessage.apply(userB.id, _))

          sendAndReceiveMessages(from = userA, outgoingMessages).discard()
          val receivedMessages = sendAndReceiveMessages(userB, Nil)._2

          receivedMessages.size shouldBe outgoingMessages.size
          receivedMessages
            .zip(outgoingMessages)
            .foreach {
              case (received, sent) =>
                received.from shouldBe userA.id
                received.to shouldBe userB.id
                received.payload shouldBe sent.payload
            }
        }
    }
  }
}
