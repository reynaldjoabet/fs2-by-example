import cats.effect._
import cats.effect.kernel.Sync
import cats.effect.std._
import fs2._
import fs2.concurrent.Channel

object ChannelExample extends IOApp {

  def broadcastExample[F[_]: Concurrent: Sync: Console]: Stream[F, Unit] = {
    for {
      channel <- Stream.eval(Channel.unbounded[F, String])

      _ <- Stream.unfold(0)(s => Some(s -> (s + 1))).evalMap(i => channel.send(s"Message $i"))

      // Consumer
      _ <- channel.stream.evalMap(msg => Sync[F].delay(println(s"Received: $msg")))
    } yield ()
  } // .evalTap(Console[F].println)

  override def run(args: List[String]): IO[ExitCode] =
    broadcastExample[IO].compile.drain.as(ExitCode.Success)
//You want to create a bounded channel where producers may be blocked if the channel is full. This is useful for backpressure.

}
