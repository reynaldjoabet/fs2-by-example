import cats.effect._
import fs2.concurrent.Topic

final case class ChatServer()

object ChatServer {

  val chats = Topic[IO, String].flatMap { topic =>
    topic.subscribe(500).filter(_.length > 7).compile.drain
  }

}
