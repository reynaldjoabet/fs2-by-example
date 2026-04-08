import scala.concurrent.duration._
import scala.util.Random

import cats.effect._
import fs2._
import fs2.concurrent.SignallingRef

object Signals extends IOApp.Simple {

  override def run: IO[Unit] = {
    def signaller(signal: SignallingRef[IO, Boolean]): Stream[IO, Nothing] = {
      Stream
        .repeatEval(IO(Random.between(1, 1000)).flatTap(i => IO.println(s"Generating $i")))
        .metered(100.millis)
        .evalMap(i => if (i % 5 == 0) signal.set(true) else IO.unit)
        .drain
    }

    Stream(7).repeatPull(
      _.uncons
        .flatMap {
          case None    => ???
          case Some(_) => ???
        }
    )
    def worker(signal: SignallingRef[IO, Boolean]): Stream[IO, Nothing] =
      Stream.repeatEval(IO.println("Working...")).metered(50.millis).interruptWhen(signal).drain

    Stream
      .eval(SignallingRef[IO, Boolean](false))
      .flatMap { signal =>
        worker(signal).concurrently(signaller(signal))
      }
      .compile
      .drain

    type Temperature = Double
    def createTemperatureSensor(
      alarm: SignallingRef[IO, Temperature],
      threshold: Temperature
    ): Stream[IO, Nothing] =
      Stream
        .repeatEval(IO(Random.between(-40.0, 40.0)))
        .evalTap(t => IO.println(f"Current temperature: $t%.1f"))
        .evalMap(t => if (t > threshold) alarm.set(t) else IO.unit)
        .metered(300.millis)
        .drain

    def createCooler(alarm: SignallingRef[IO, Temperature]): Stream[IO, Nothing] =
      alarm.discrete.evalMap(t => IO.println(f"$t%.1f °C is too hot! Cooling down...")).drain

    val threshold          = 20.0
    val initialTemperature = 20.0

    // Exercise
    // Create a stream that emits a signal
    // Create a temperature sensor and a cooler
    // Run them concurrently
    // Interrupt after 3 seconds
    val program = Stream
      .eval(SignallingRef[IO, Temperature](initialTemperature))
      .flatMap { alarm =>
        val temperatureSensor = createTemperatureSensor(alarm, threshold)
        val cooler            = createCooler(alarm)
        cooler.merge(temperatureSensor)
      }
    program.interruptAfter(3.seconds).compile.drain
  }

  Stream(4).pull.echoChunk

}

// Signal: A concurrent, mutable reference that can be read and written. Signals provide a way to share state across different parts of a streaming application.
// SignallingRef: A variant of Signal that supports listening for changes, allowing streams to react to state changes.

// Deferred: A concurrency primitive that represents a value which will be computed or provided in the future. It can be thought of as a one-time promise or future.
// Usage: Often used for signaling completion or coordinating between tasks where one task needs to wait for a value to be provided by another.

// Semaphore: A counting semaphore for controlling access to a resource. It can be used to limit the number of concurrent accesses to a resource or to coordinate multiple tasks.
// Usage: Managing resource pools, limiting concurrency levels, or implementing more complex synchronization schemes
