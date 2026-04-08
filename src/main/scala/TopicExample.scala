import scala.concurrent.duration._

import cats.effect._
import fs2._
import fs2.concurrent.Signal
import fs2.concurrent.Topic

object TopicExample extends IOApp {

  val stream2 =
    fs2
      .Stream
      .unfold(1)(x => Some(x, x + 1))
      .chunks
      .mapAccumulate(1)((l, c) => (c.size + l, c))
      .evalMap(IO.println)
      .as(2)
      .chunks
      .covary[IO]

  val consumer2: Pipe[IO, Chunk[Int], Unit] =
    _.evalMap(i => IO.println(s"Read $i from consumer2"))

  val consumer1: Pipe[IO, Chunk[Int], Unit] =
    _.evalMap(i => IO.println(s"Read $i from consumer1"))

  val consumer3: Pipe[IO, Chunk[Int], Unit] =
    _.evalMap(i => IO.println(s"Read $i from consumer3"))

  stream2.pull.uncons

  // Stream.resource()

  override def run(args: List[String]): IO[ExitCode] = stream2
    // .metered(2.seconds)
    // .buffer(3)
    .broadcastThrough(consumer1, consumer2, consumer3).compile.drain.as(ExitCode.Success)

  Stream(1, 2, 3, 4).intersperse("\n").toList

}

// fs2 lets you write Streams that do not perform any effect, and they have type Stream[Pure, A]
// this is useful sometimes to do similar things you'd do with scala collections, but with a richer api
// when you compile.toList a Stream[Pure, A], you simply get List[A] back
// when dealing with effectful code instead, you have Stream[IO, A], to signify that the Stream is doing effects via IO
// and when you compile it, you will get an IO back, i.e. compile.toList returns IO[List[A]], and compile.drain returns IO[Unit]
// this means that some operations inside fs2 have type Stream[Pure, A], but they can also be used as part of Stream[IO, A]. Stream.emit is an example
// now, most of this machinery is completely transparent to you, because effects in fs2 are covariant
// just like Animal <: Dog implies List[Animal] <: List[Dog] (<: means is a subtype of)
// Pure <: IO implies Stream[Pure, A] <: Stream[IO, A]
// this property is called covariance, and it's a general scala concept
// now as I said, most of the time all of this is completely transparent to you, we took great care designing the api so that it Just Works™ most of the time.
// There are some cases where the compiler cannot quite tell that an operation with type Stream[Pure, A] needs to be used with type Stream[IO, A], and covary is a nudge to the compiler to give it the right type.
// So Stream.emit(1, 2, 3): Stream[Pure, Int], Stream.emit(1, 2, 3).covary[IO]: Stream[IO, Int]
