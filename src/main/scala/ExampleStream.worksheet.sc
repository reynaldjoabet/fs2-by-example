import java.math.BigInteger

import scala.concurrent.duration._
import scala.concurrent.Future

import cats.effect.kernel.Async
import cats.effect.unsafe.IORuntime
import cats.effect.IO
import cats.instances.stream
import fs2._
import fs2.Pure

val unfold1 = fs2.Stream.unfold(1)(x => Some(x -> (x - 1)))
val unfold  = fs2.Stream.unfold(0)(i => if (i < 20) Some(i -> (i + 1)) else None)

unfold.toList

val emit = fs2.Stream.emit(12)
emit.toList
val empty = fs2.Stream.empty
empty.toList

val chunk = fs2
  .Stream
  .unfoldChunk(0)(i => if (i < 5) Some(Chunk.seq(List.fill(i)(i)) -> (i + 1)) else None)

chunk.toList

val emit1 = fs2.Stream.emit(Chunk.seq(1 to 20))

emit1.evalMap(c => IO(c.toList)).compile.toList

val io = IO(12)

fs2.Stream.unfoldLoop(0)(i => (i, if (i < 5) Some(i + 1) else None)).toList

fs2.Stream.emits('A' to 'E').toList

fs2
  .Stream
  .emits[IO, Char]('A' to 'E')
  .map(letter => fs2.Stream.emits[IO, Int](1 to 3).map(index => s"$letter$index"))
  .parJoin(5)

val s = fs2.Stream(1, 2) ++ fs2.Stream(3) ++ fs2.Stream(4, 5, 6)
s.chunks.toList

fs2
  .Stream(1, 2, 3)
  .append(fs2.Stream(4, 5, 6))
  .mapChunks { c =>
    val ints = c.toArraySlice;
    for (i <- 0 until ints.values.size) ints.values(i) = 0; ints
  }
  .toList

fs2
  .Stream("Hello", "Hi", "Greetings", "Hey")
  .groupAdjacentBy(_.head)
  .toList
  .map { case (k, vs) => k -> vs.toList }

def lettersIter: fs2.Stream[Pure, Char] = fs2.Stream.emits('A' to 'Z')

lettersIter.toList

def lettersUnfold = fs2.Stream.unfold('a')(let => Some(let, (let + 1).toChar))

lettersUnfold.take(26).toList

val numbers = fs2.Stream.iterate(0)(_ + 1)

numbers.take(12).toList

def myIterate[A](initial: A)(next: A => A): fs2.Stream[Pure, A] =
  fs2.Stream.unfold(initial)(init => Some(init -> next(init)))

myIterate(0)(_ + 1).take(12).toList

def repeat[A](stream: fs2.Stream[Pure, A]): fs2.Stream[Pure, A] = stream.repeat

repeat(fs2.Stream.emits(1 to 5)).take(10).toList

def unNone[A](stream: fs2.Stream[Pure, Option[A]]): fs2.Stream[Pure, A] =
  stream.flatMap {
    case None        => fs2.Stream.empty
    case Some(value) => fs2.Stream.emit(value)
  }

def unNone1[A](stream: fs2.Stream[Pure, Option[A]]): fs2.Stream[Pure, A] =
  stream.collect { case Some(value) => value }

def unNone2[A](stream: fs2.Stream[Pure, Option[A]]): fs2.Stream[Pure, A] =
  stream.flatMap(opt => fs2.Stream.fromOption(opt))

val k = fs2.Stream.unfold(Some(12))(num => Some(num, Some(num.get * 2)))
unNone(k).take(6).toList

unNone1(fs2.Stream(Some(12), None, None, Some(2), None)).toList

unNone2(fs2.Stream(Some(12), None, None, Some(2), None, Some(89))).toList

fs2
  .Stream
  .constant(2)
  .zipWith(fs2.Stream.iterate(1)(_ + 1))(_ * _)
  .take(12)
  .toList
  .groupBy(_ % 2 == 0)
import scala.concurrent.ExecutionContext.Implicits.global
val h = Future(8)
Future.sequence(List(h))

(Stream(1, 2, 3) ++ Stream(4, 5, 6)).toList

val list = (1 to 10).toList.partition(_ % 2 == 0)
list
import cats.effect.SyncIO
(Stream(1, 2, 3) ++ Stream.raiseError[SyncIO](new RuntimeException) ++ Stream(
  4,
  5,
  6
)).attempt.compile.toList.unsafeRunSync()
val list1 = (1 to 190).toList.groupBy(_ % 2 == 0)
list1

unfold1.take(120000).toList
import cats.effect.unsafe.implicits.global
implicit val ec = IORuntime.global
Stream
  .unfoldLoopEval[IO, Int, Int](0)(s => if (s > 20) IO((s, None)) else IO(s, Some(s + 1)))
  .compile
  .toList
  .unsafeRunSync()

val s1 = Stream.eval(IO(1)).metered(100.millis).repeatN(20)
val s2 = Stream.eval(IO(2)).metered(100.millis).repeatN(20)

val s3 = s1.merge(s2)

val s4 = s3
  .evalTap(IO.println)
  .map { x =>
    println(x); x
  }

s4.compile.drain.unsafeRunSync() //.unsafeRunSync()
//So it merges them, but whichever emits first gets displayed.

val s5 = s2
  .interleave(s1)
  .evalTap(IO.println)
  .map { x =>
    println(x); x
  }

s5.compile.drain.unsafeRunSync()

Stream.emits(1 to 40).intersperse(",").toList
val helloPull = Stream("hello").pull

helloPull.echo

//This has an output type of Nothing, meaning it doesn’t output any values, and a result of String
Pull.pure("hello")

def unconsList(li: List[Int]) = li match {
  case head :: next => Some(head -> next)
  case Nil          => None
}

unconsList((1 to 100).toList).map(_._1)

unconsList((1 to 100).toList).map(_._2)

//fs2.Stream.timeout(23.seconds)

Stream(1, 2, 3, 4).covary[IO].parEvalMap(2)(i => IO(println(i))).compile.drain.unsafeRunSync()

abstract class Student

case object Undergraduate extends Student
case object Postgraduate  extends Student
case object Masters       extends Student

def s(s: Student) = s match {
  case Masters => 1
  case other   => 2
}

s(Undergraduate)
s(Masters)
s(Postgraduate)

// fs2.Stream
//     .unfold(1)(x => Some(x, x + 1))
//     //.chunks
//     .toList

Stream(1, 2, 3, 4).intersperse("\n").toList

Stream(1, 2, 3, 4).zipWithNext.toList
Stream.repeatEval(IO(65))

val n = Stream("ok", "skip", "next ok", "skip", "told you")
  .pull
  .takeWhile(o => o != "skip")
  .flatMap {
    case None => Pull.done

    case Some(s) => s.drop(1).pull.echo
  }
  .stream
  .toList

import scala.concurrent.duration.FiniteDuration

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

}

Stream.repeatEval(IO((1 to 100).toList)).flatMap(Stream.emits(_)).mapChunks(_.map(_ + 2))

//Stream.emits(8)
s1.chunks.mapChunks(_ => ???)
// it can be used by utilizing .through(StreamUtils.timeoutOnIdle(5.seconds))

s1.pull.uncons // This constructs a pull that pulls the next chunk from the stream.

//We use uncons to pull the next chunk from the stream, giving us a Pull[F,Nothing,Option[(Chunk[O],Stream[F,O])]]

//We finish pulling with Pull.done, a pull that does nothing.

Stream(1, 2, 3)
  .append(Stream(4, 5, 6))
  .mapChunks { c =>
    println(c); c.map(_ + 2)
  }
  .toList

((1 to 10) ++ (11 to 20)).mkString("&")

val buses = List("110")
val trams = List("31", "33")(buses ++ trams).mkString("&")

Stream.iterate(0)(_ + 1).filterNot(_ % 2 == 0).sliding(3).take(15).toList

Stream.iterate(0)(_ + 1).filterNot(_ % 2 == 0).sliding(3).map(_.foldLeft(0)(_ + _)).take(15).toList
Stream.fixedRateStartImmediately[IO](3.seconds)

//Seq(1,23).join(Seq(2,3))

Stream.emits(1 to 200).take(10).toList

Stream.emits(1 to 200).takeWhile(_ != 5).take(10).toList //List[Int] = List(1, 2, 3, 4)

Stream.emits(1 to 200).takeWhile(_ % 2 == 0).take(10).toList // List()

Stream.emits(2 to 200).takeWhile(_ % 2 == 0).take(10).toList //List(2)

def dedupeConsecutiveWhen[F[_], I](f: (I, I) => Boolean): Pipe[F, I, I] = in =>
  in.zipWithNext
    .map {
      case (curr, Some(next)) if f(curr, next) => None: Option[I]
      case (curr, Some(next))                  => Some(curr)
      case (curr, None)                        => Some(curr)
    }
    .unNone

Stream(1, 1, 2, 2, 3, 4, 5, 2).through(dedupeConsecutiveWhen(_ == _)).toList

Stream.chunk(Chunk(1, 23, 3)).groupAdjacentBy(identity).map(_._2).compile.to(Chunk).toArray

val fibonacciStream = Stream
  .unfold((BigInt(0), BigInt(1))) { case (a, b) =>
    val next = a + b
    Some((a, (b, next)))
  }
  .covary[IO]
  .take(100)
  .compile
  .toList
  .unsafeRunSync()

LazyList.unfold(BigInt(0), BigInt(1)) { case (a, b) =>
  val next = a + b
  Some((a, (b, next)))

}

val factorialStream = Stream
  .unfold(BigInt(1), BigInt(1)) { case (acc, n) =>
    Some((acc, (acc * (n + 1), n + 1)))
  }
  .covary[IO]
  .take(5)
  .compile
  .toList
  .unsafeRunSync()
  .last

LazyList.unfold(BigInt(1), BigInt(1)) { case (acc, n) =>
  Some((acc, (acc * (n + 1), n + 1)))
}

List(3, 5).fold(0)((a, b) => a + b)

//how do i go from fs2.Stream[_, Option[T]] to an fs2.Stream[_, T] by discarding option values
Stream(Some(1), Some(2), None, Some(3), None).unNone.toList

Stream.emits(1 to 10).map(Some(_)).unNoneTerminate.toList
