import scala.concurrent.duration._

import cats.effect._
import fs2._
import fs2.concurrent._

object Topics extends IOApp.Simple {

//Using Topic for Broadcasting
  override def run: IO[Unit] = {
    Stream
      .eval(Topic[IO, Int])
      .flatMap { topic =>
        val p1 = Stream.iterate(0)(_ + 1).covary[IO].through(topic.publish).drain
        val p2 = Stream.iterate(0)(_ + 5).covary[IO].through(topic.publish).drain
        val p3 = Stream.unfold(0)(s => Some(s, s + 11))
        val c1 = topic.subscribe(1000).evalMap(i => IO.println(s"Read $i from c1")).drain
        val c2 = topic.subscribe(1000).evalMap(i => IO.println(s"Read $i from c2")).drain // .metered(100.millis).drain
        Stream(p1, p2, p3, c1, c2).parJoinUnbounded
      }
      .interruptAfter(0.2.seconds)
      .compile
      .drain
  }

}
