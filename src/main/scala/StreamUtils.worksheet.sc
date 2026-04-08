import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

import cats.effect.kernel.Resource
import cats.effect.unsafe.implicits.global
import cats.effect.Async
import cats.effect.IO
import cats.effect.SyncIO
import fs2.{Pipe, Pull, Stream}

object StreamUtils {

  case class TimeoutException(duration: FiniteDuration)
      extends RuntimeException(s"No new messages received for $duration")

  def timeoutOnIdle[F[_]: Async, A](duration: FiniteDuration): Pipe[F, A, A] = { stream =>
    stream
      .pull
      .timed { timedPull =>
        def go(timedPull: Pull.Timed[F, A]): Pull[F, A, Unit] =
          timedPull.timeout(duration) >>
            timedPull
              .uncons
              .flatMap {
                case Some((Right(elems), next)) => Pull.output(elems) >> go(next)
                case Some((Left(_), _))         => Pull.raiseError(TimeoutException(duration))
                case None                       => Pull.done
              }

        go(timedPull)
      }
      .stream
  }

//implicit val ec = IORuntime.global
  val s = (Stream("elem") ++ Stream.sleep_[IO](1500.millis)).repeat.take(3)

  s.pull
    .timed { timedPull =>
      def go(timedPull: Pull.Timed[IO, String]): Pull[IO, String, Unit] =
        timedPull.timeout(1.second) >> // starts new timeout and stops the previous one
          timedPull
            .uncons
            .flatMap {
              case Some((Right(elems), next)) => Pull.output(elems) >> go(next)
              case Some((Left(_), next))      => Pull.output1("late!") >> go(next)
              case None                       => Pull.done
            }
      go(timedPull)
    }
    .stream
    .compile
    .toVector
    .unsafeRunSync()

}

// In fs2, the scan operation is used to perform a stateful streaming transformation on an fs2.Stream. It is similar to the scanLeft operation in Scala collections, but it operates on a stream of elements instead of a static collection.

// The scan operation in fs2 takes an initial state and a binary function that updates the state based on each incoming element of the stream. It produces a new stream of elements that represent the intermediate states as the stream is processed.

// Here's a basic example of how to use scan in fs2:

val stream = Stream(1, 2, 3, 4, 5)

val scanStream = stream.scan(1)((acc, elem) => acc + elem)

scanStream.toList

import cats.effect.std.Semaphore

val semStream = Stream
  .eval[IO, Semaphore[IO]](Semaphore(8))
  .flatMap { sem =>
    Stream
      .emits(List(1, 2, 3, 4, 5))
      .covary[IO]
      .parEvalMapUnordered(2) { i =>
        sem
          .permit
          .use { _ =>
            IO.println(s"Processing $i") >> IO.sleep(1.second)
          }
      }
  }

semStream.compile.drain.unsafeRunSync()
// Output: Concurrent processing with controlled parallelism

val parallelStream = Stream(Stream(1, 2, 3), Stream(4, 5, 6), Stream(7, 8, 9)).covary[IO]

val mergedStream = parallelStream.parJoin(3)

mergedStream.compile.toList.unsafeRunSync()
// Output: List(1, 4, 7, 2, 5, 8, 3, 6, 9)

Stream(1, 2, 3, 4, 5, 9, 0, 0, 0, 877, 7, 90, 5, 789, 87)
  .covary[IO]
  .parEvalMapUnordered(6)(i => IO(println(i)))
  .compile
  .drain
  .unsafeRunSync()

val resources: Stream[IO, Resource[IO, Int]] = Stream(
  Resource.eval(IO(1)),
  Resource.eval(IO(2)),
  Resource.eval(IO(3))
)

resources.parEvalMap(3)(_.use(r => IO.println(s"this is amazing $r"))).compile.drain.unsafeRunSync()

import scala.concurrent.duration._

import cats.effect.unsafe.implicits.global
import cats.effect.IO

val s = Stream(1) ++ Stream
  .sleep_[IO](100.millis) ++ Stream(2).repeat.meteredStartImmediately[IO](200.millis)

s.timeoutOnPullTo(150.millis, Stream(3, 4)).compile.toVector.unsafeRunSync()

//Transforms this pull with the function `f` whenever an element is not emitted within
// the duration `t`.

//++ is left-associative, so the concatenation operation is applied to the streams from left to right.
val ss = (Stream("elem") ++ Stream.sleep_[IO](600.millis)).repeat.take(3)
ss.pull.timeoutWith(450.millis)(Pull.output1("late!") >> _).stream.compile.toVector.unsafeRunSync()
// Vector[String] = Vector(elem, late!, elem, late!, elem)
