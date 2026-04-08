//> using dep "co.fs2::fs2-core:3.9.2"

import scala.concurrent.duration._

import cats.effect
//> using scala "2.13.10"
import cats.effect._
import cats.effect.Deferred
import fs2.concurrent.SignallingRef
import fs2.concurrent.Topic
import fs2.Stream
import fs2.Stream._

object ExampleStreams extends IOApp {

  val stream1 = Stream.emits(1 to 100000).covary[IO].evalMap(IO.println)

  override def run(args: List[String]): IO[ExitCode] =
    fibonacciStream.compile.drain.as(ExitCode.Success)
  // consumerImpl.subscribe(
  // iterableStream
  // ) !> topic !> awakeEvery !> (evalParStream).as(ExitCode.Success)
  // killSwitch.as(ExitCode.Success)
  // stream1.compile.drain.as(ExitCode.Success)

  val unfold = Stream.unfold(0)(seed => Some(seed, seed + 1)).evalTap(IO.println)
//batching
//A Chunk is a finite sequence of values that is used by fs streams internally:
  val batchedStream = Stream.emits(1 to 100).chunkN(20).evalMap(IO.println).compile.drain

//And if you want to batch within a specific time window groupedWithin is what you need:

  val batchWithinWindow = Stream
    .awakeEvery[IO](6.millis)
    .groupWithin(100, 100.millis)
    .evalTap(chunk => IO(println(s"Processing batch of ${chunk.size} elements")))
    .compile
    .drain

  // parEvalMap preserves the stream ordering.
  // If this is not required there is a parEvalMapUnordered method.

//mapAsync (and mapAsyncUnordered) methods that are just aliases for parEvalMap (and parEvalMapUnordered respectively)
  def killSwitch =
    SignallingRef
      .of[IO, Boolean](false)
      .flatMap { signal =>
        unfold
          .interruptWhen(signal)
          .concurrently(
            Stream.sleep[IO](60.seconds) >> Stream.eval(signal.set(true))
          )
          .compile
          .drain
      }

  // ###Throttling
  // Fs2 provides a mechanism to create a stream that emits an element on a fix interval.
  // If zipped to another stream it limits the rate of the second stream:

  val throttledStream =
    Stream.awakeEvery[IO](1.second).zipRight(Stream.emits(1 to 100))
  // As it’s a very common pattern fs2 provide us with a method metered that do just that.

  // If instead of limiting the rate of the stream you prefer to discard some elements you can use debounce

  val ints = Stream.constant[IO, Int](1).scan1(_ + _) // 1, 2, 3, ...
  ints.debounce(1.second)
//This emits an element at a fixed rate discarding every element produced in between

//A router is machine that forwards packets from one network to another
//To accomplish this, the router must have atleast two network interfaces
//A machine with only one network interface cannot forward packets; it is considered a host

//Ledgers are the basic storage unit in BookKeeper, also referred to as segments in Pulsar.
//Pulsar maintains a ledger in BookKeeper for each subscription
  /**
    * BookKeeper also has a special kind of ledger, or the cursor ledger. Cursors provide a tracking
    * mechanism for message consumption and acknowledgment in Pulsar. Each subscription has a cursor
    * associated with it which stores the position information of message consumption and
    * acknowledgment. Consumers may share the same cursor depending on the subscription type.
    */
//Ledgers are the smallest unit for deletion, which means you can only delete a ledger as a whole instead of deleting individual entries within the ledger.
//To create multiple concurrent processing, we can use parEvalMap(nWorker:Int)(f:A => F[A1])

  val evalParStream = Stream
    .emits(1 to 100)
    .covary[IO]
    .parEvalMap(2)(x => IO.println(x))
    .compile
    .drain

  val awakeEvery = Stream
    .awakeEvery[IO](2.seconds)
    .evalMap(x => IO.println(x.toString()))
    .compile
    .drain

  val constantStream = Stream.constant(12)

  val durationStream = Stream.duration[IO]

  val fixedRateStreams = Stream.fixedRate[IO](2.seconds)

  val iterableStream = Stream.iterable(1 to 10000)

  trait Consumer[F[_], A] {
    def subscribe(upstream: Stream[F, A]): F[Unit]
  }

  val consumerImpl = new Consumer[IO, Int] {

    override def subscribe(upstream: Stream[IO, Int]): IO[Unit] =
      upstream.metered(1.seconds).zip(durationStream).evalMap(IO.println).compile.drain

  }

//val f= Stream.fromQueueNoneTerminated()

  val topic = Topic[IO, Int].flatMap { topic =>
    val publisher = Stream.unfold(1)(x => Some(x, x + 1)).through(topic.publish)

    val subscriber = topic.subscribe(20).evalTap(IO.println)

    subscriber.concurrently(publisher).compile.drain

  }

////val f=Stream.emits(1 to 300).ev
  val fibonacciStream = Stream
    .unfold((0, 1)) { case (a, b) =>
      val next = a + b
      Some((next, (b, next)))
    }
    .covary[IO]
    .take(13)
    .evalTap(IO.println)

}
