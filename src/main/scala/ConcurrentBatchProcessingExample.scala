import scala.concurrent.duration._

import cats.effect._
import cats.effect.std.Semaphore
import fs2.concurrent.SignallingRef
import fs2.Pull
import fs2.Stream

// SignallingRef
// Purpose: Represents a mutable reference to a value, allowing subscribers to be notified of changes.
// Usage: Useful for scenarios where you need to broadcast state changes to multiple subscribers.

object SignallingRefExample extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    program.as(ExitCode.Success)

  val program = for {
    signal     <- SignallingRef.of[IO, Int](1)
    incrementer = Stream.awakeEvery[IO](1.second).evalMap(_ => signal.update(_ + 1))
    watcher     = signal.discrete.evalMap(i => IO(println(s"Signal updated: $i")))
    _          <- Stream(incrementer, watcher).parJoinUnbounded.compile.drain
  } yield ()

}

// Deferred
// Purpose: Represents a value that may not yet be available.
// Usage: Useful for one-time signals between concurrent processes.

object DeferredExample extends IOApp.Simple {

  def run: IO[Unit] = for {
    deferred <- Deferred[IO, Int]
    producer  = Stream.eval(IO.sleep(1.second) *> deferred.complete(42)).drain
    consumer  = Stream.eval(deferred.get).evalMap(value => IO(println(s"Received: $value")))
    _        <- Stream(producer, consumer).parJoinUnbounded.compile.drain
  } yield ()

  Stream
    .unit
    .repeatPull(
      _.uncons
        .flatMap {
          case Some(value) => ???
          case None        => ???
        }
    )
  Stream(5, 8).pull.echo

}

// Semaphore
// Purpose: Provides a way to control concurrency with a limited number of permits.
// Usage: Useful when you need to limit the number of concurrent operations.

object SemaphoreExample extends IOApp.Simple {

  def run: IO[Unit] = for {
    semaphore <- Semaphore[IO](1)
    task = Stream
             .resource(semaphore.permit)
             .evalMap(_ => IO(println("Running task")).>>(IO.sleep(5.seconds)))
    _ <- Stream(task, task, task).parJoinUnbounded.compile.drain
  } yield ()

}

// Stops pulling from this stream if it does not emit a new chunk within the
//given `timeout` after it is requested, and starts pulling from the `onTimeout` stream instead.

object timeoutOnPullTo {

  import scala.concurrent.duration._

  import cats.effect.unsafe.implicits.global
  import cats.effect.IO

  val s = Stream(1) ++ Stream
    .sleep_[IO](100.millis) ++ Stream(2).repeat.meteredStartImmediately[IO](200.millis)

  s.timeoutOnPullTo(150.millis, Stream(3)).compile.toVector.unsafeRunSync()
  // Vector[Int] = Vector(1, 2, 3)

}

//  Allows expressing `Pull` computations whose `uncons` can receive
//   a user-controlled, resettable `timeout`.
//    See [[Pull.Timed]] for more info on timed `uncons` and `timeout`.

//  As a quick example, let's write a timed pull which emits the
//    string "late!" whenever a chunk of the stream is not emitted
//    within 1 second:

object timedExample {

  import scala.concurrent.duration._

  import cats.effect.unsafe.implicits.global
  import cats.effect.IO

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
  // Vector[String] = Vector(elem, late!, elem, late!, elem)

}

//FS2 streams are based on a pull-based architecture where downstream consumers request data from upstream producers.

//Stream: A Stream is essentially a sequence of Pull steps. Each step can produce a chunk of elements, update state, or perform side effects.
//Stream: A Stream is essentially a sequence of Pull steps.
// In the case of an infinite stream, the Pull steps are designed to never reach a termination condition unless explicitly controlled by the consumer. This allows the stream to continue producing elements indefinitely
// Laziness and Evaluation Control
// FS2 streams are designed to be lazy. This means that the elements of a stream are not computed until they are needed by a consumer. Here's how laziness helps in creating infinite streams:

// Deferred Evaluation: Elements are produced only when downstream consumers pull for them, allowing the stream to potentially produce an infinite sequence of elements.
// Backpressure: Consumers can control the rate at which they pull elements from the stream, ensuring that producers do not overwhelm them.

//Enqueues the elements of this stream to the supplied queue.
