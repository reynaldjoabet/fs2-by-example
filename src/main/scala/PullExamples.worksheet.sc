import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration._
import scala.concurrent.Future
import scala.language.postfixOps

import cats.effect._
import cats.effect.unsafe.IORuntime
import cats.effect.SyncIO
import cats.implicits._
import cats.instances.stream
import cats.kernel.Monoid
import fs2._
import fs2.concurrent.SignallingRef
import fs2.Pure

implicit val ec: IORuntime = IORuntime.global

val pull = Pull.eval(IO(23))

val chunkPull = Pull.output(Chunk(1, 2, 3, 4, 5, 6))

val donePull = Pull.done

//val f= Pull.raiseError( new RuntimeException())

val purePull = Pull.pure(23).void.streamNoScope

val chunkOne = Pull.output1(2)

//  def outputOption1[F[_], O](opt: Option[O]): Pull[F, O, Unit] =
//   opt.map(output1).getOrElse(done)
val optionChunkOne = Pull.outputOption1(Some(6))

val noneChunk = Pull.outputOption1(None)

val duration1 = Duration("5 seconds")
val duration2 = Duration(5, TimeUnit.SECONDS)
val duration3 = 5.seconds
val duration4 = 250.millis
val sleepPull = Pull.sleep[IO](6 seconds)

val loopPull = Pull.loop[IO, Int, Option[Unit]](r => Pull.done.map(_ => None))

val buf = new scala.collection.mutable.ListBuffer[String]()
Stream
  .range(0, 45)
  .covary[SyncIO]
  .evalMap(i => SyncIO { buf += s">$i"; i })
  .buffer(4)
  .evalMap(i => SyncIO { buf += s"<$i"; i })
  .compile
  .toVector
  .unsafeRunSync()

(Stream(1) ++ Stream(2, 3) ++ Stream(4, 5, 6)).chunkAll.toList

(Stream(1) ++ Stream(2, 3) ++ Stream(4, 5, 6)).chunks.toList

Stream.emits(1 to 100).chunkLimit(5).toList

val s =
  Stream(1, 2, 3) ++ Stream.sleep_[IO](500.millis) ++ Stream(4, 5) ++ Stream
    .sleep_[IO](10.millis) ++ Stream(6)

s.debounce(100.milliseconds).compile.toVector.unsafeRunSync()

Stream(1, 2, 3).noneTerminate.toList

(fs2.Stream.empty *> fs2.Stream.emit(1)).toList

//*> means flatMap and  flatMap expects a A => Stream[IO, B]
//So there has to be at least one A in order to create the second stream

(fs2.Stream.empty ++ fs2.Stream.emit(1)).toList // short circuits

val j = for {
  a <- List(1, 2)
  b <- List.empty[Int]
} yield (a, b)

val jf = for {
  a <- List.empty[Int]
  b <- List(1, 2)
} yield (a, b)

def sumOperator[F[_], A: Monoid](windowSize: Int): Pipe[F, A, A] = { in =>
  def go(stream: Stream[F, A]): Pull[F, A, Unit] = {
    stream
      .pull
      .unconsN(windowSize, true)
      .flatMap {
        case None => Pull.done
        case Some((chunk, tl)) =>
          Pull.output1(
            chunk.foldLeft(Monoid[A].empty)((acc, a) => Monoid[A].combine(acc, a))
          ) >> go(tl)
        // case Some((chunk,tl)) =>Pull.output1( chunk.foldLeft(Monoid[A].empty)(_ |+| _))>>go(tl)
      }
  }
  go(in).stream
}

List(1, 2).flatMap(x => List.empty[Int])
Stream.empty

Stream("hello").toList

Pull.output1[Pure, String]("Hello").stream.compile.toList

def take(s: Stream[IO, Int], size: Int) = s
  .pull
  .unconsN(size)
  .flatMap {
    case None              => Pull.done
    case Some((chunk, tl)) => Pull.output(chunk)
  }
  .stream

take(Stream.emits(1 to 100), 8)
  .map { x =>
    println(x); x
  }
  .compile
  .drain
  .unsafeRunSync()
//Stream.resource()

Stream.emits(1 to 900).through(sumOperator(5)).toList

Stream(7).pull.echo // convert a Stream to a Pull

//I was wondering if it's possible to start a stream based on  change of value on SignallingRef[IO, Boolean], from false to true.
//Or if a stream can sleep infinitely then wake up based on a change in the signal.

SignallingRef.apply[IO, Boolean](true).map(_.discrete)

SignallingRef[IO, Boolean](false).flatMap { signal =>
  val s1 = Stream.awakeEvery[IO](1.second).interruptWhen(signal).map(_ => 1)
  val s2 = Stream.sleep[IO](4.seconds) >> Stream.eval(signal.set(true)).map(_ => 2)
  s1.concurrently(s2).compile.toVector
}

Chunk(1, 2, 4, 4).splitAt(1)

(Stream(1) ++ Stream(2, 3) ++ Stream(4, 5, 6)).chunks.toList

Stream(1, 2, 3, 4).evalMap(i => SyncIO(println(i))).take(2).compile.drain.unsafeRunSync()

Stream(1, 2, 3, 4).evalMapChunk(i => SyncIO(println(i))).take(2).compile.drain.unsafeRunSync()

import fs2.io._

Stream(1).covary[IO].pull.uncons

Stream.unfold(0)(s => Some(s, s + 10)).take(4).toList

def sumPairs[F[_]]: Stream[F, Int] => Stream[F, Int] = { stream =>
  def go(acc: Int, s: Stream[F, Int]): Pull[F, Int, Unit] = {
    s.pull
      .uncons
      .flatMap {
        case Some((chunk, tail)) =>
          // If we have at least two elements, sum the first two and continue
          if (chunk.size >= 2) {
            val sum = chunk(0) + chunk(1) + acc
            Pull.output1(sum) >> go(sum, tail.cons(chunk.drop(2)))
          } else {
            // Handle the case where there's one element left
            Pull.output(chunk) >> Pull.done
          }
        case None => Pull.done
      }
  }
  go(0, stream).stream
}

val source: Stream[IO, Int] = Stream.emits(List(1, 2, 3, 4, 5))
val result: Stream[IO, Int] = source.through(sumPairs)
result.compile.toList.flatMap(res => IO(println(s"Result: $res"))).unsafeRunSync()

List(4).tail.nonEmpty
Stream(1, 2, 3).noneTerminate.toList

Stream.constant(2, 10).debugChunks(x => x.toString()).take(50).compile.toList
