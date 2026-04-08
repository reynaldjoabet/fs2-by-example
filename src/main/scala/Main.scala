import cats.effect._
import cats.effect.Concurrent
import cats.effect.LiftIO
import cats.effect.Sync
import cats.implicits.{catsSyntaxApplicativeError, toFunctorOps}
import fs2._
import fs2.{hash, text}
import fs2.hashing.*
import fs2.io.file.{Files, Path}
import fs2.Compiler

object Main extends IOApp {

  val fibonacciStream = Stream.unfold(0)(x => Some(x + (x + 1), x + 1))

  val p = (x: Int) => IO.println(s"this is an Int $x")

  val q    = (x: Int) => IO.println(s"this is a String ${x.toString()}")
  val ints = Pull.pure[IO, Int](2).as(()).stream // .void.stream.evalTap{IO.println}

  override def run(args: List[String]): IO[ExitCode] =
    Stream(1, 2).evalTap(p).evalMap(q).compile.drain.as(ExitCode.Success)

//Stream.unfold()
  Stream(1, 2, 3).cons(Chunk(-1, 0)).toList

  Stream.eval(IO(7)).evalMap(x => IO(x + 33))
  Stream.emits(1 to 90).covary[IO].pull

  val b = Pull.done.stream

  val h = Pull.output1[IO, Int](1)

  Stream(1, 2).pull.echo

  final case class MyStudent(name: String, age: Int)

  def d(s: MyStudent) = s match {
    case MyStudent(name, age) =>
  }

  object FilesApp {

    def writeDigest[F[_]: Files: Concurrent: Sync: LiftIO](path: Path) = {
      val target = Path(path.toString + ".sha256")
      Files[F]
        .readAll(path)
        .through(Hashing[F].hash(HashAlgorithm.SHA256))
        .map(hash => hash.toString())
        .through(text.utf8.encode)
        .through(Files[F].writeAll(target))
        .compile
        .drain

    }

    def totalBytes[F[_]: Files: Concurrent](path: Path): F[Long] =
      Files[F].walk(path).evalMap(p => Files[F].size(p).handleError(_ => 0L)).compile.foldMonoid

    def scalaLineCount[F[_]: Files: Concurrent](path: Path): F[Long] =
      Files[F]
        .walk(path)
        .filter(_.extName == ".scala")
        .flatMap { p =>
          Files[F].readAll(p).through(text.utf8.decode).through(text.lines).as(1L)
        }
        .compile
        .foldMonoid

  }

  Stream.iterate(0)(_ + 1).filter(_ % 2 == 0).sliding(3).map(_.foldLeft(0)(_ + _)).take(15).toList

  val producer = Stream.chunk(Chunk(1, 2)) ++ Stream.chunk(Chunk(3)) ++ Stream
    .chunk(Chunk(4, 5, 6, 7))

}
