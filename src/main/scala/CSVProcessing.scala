import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.{Files => JFiles}
import java.nio.file.Paths

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.Using

import cats.effect._
import cats.effect.std.Queue
import fs2._
import fs2.io.file._

object CSVProcessing extends IOApp.Simple {

  case class LegoSet(
    id: String,
    name: String,
    year: Int,
    themeId: Int,
    numParts: Int
  )

  private def parseLegoSet(line: String): Option[LegoSet] = {
    val splitted = line.split(",")
    Try(
      LegoSet(
        id = splitted(0),
        name = splitted(1),
        year = splitted(2).toInt,
        themeId = splitted(3).toInt,
        numParts = splitted(4).toInt
      )
    ).toOption
  }

  def readLegoSetImperative(
    filename: String,
    predicate: LegoSet => Boolean,
    limit: Int
  ): List[LegoSet] = {
    var reader: BufferedReader        = null
    val legoSets: ListBuffer[LegoSet] = ListBuffer.empty
    try {
      reader = new BufferedReader(new FileReader(filename))

      var line: String = reader.readLine()
      var count        = 0
      while (line != null && count < limit) {
        val legoSet = parseLegoSet(line)
        legoSet
          .filter(predicate)
          .foreach { lego =>
            legoSets.append(lego)
            count += 1
          }
        line = reader.readLine()
      }
    } finally reader.close()
    legoSets.toList
//legoSets.toList.filter(predicate).take(limit)
  }

  def readLegoSetList(
    filename: String,
    predicate: LegoSet => Boolean,
    limit: Int
  ): List[LegoSet] = {
    JFiles
      .readAllLines(Paths.get(filename))
      .asScala
      .flatMap(parseLegoSet)
      .filter(predicate)
      .take(limit)
      .toList

  }

  def readLegoSetIterator(
    filename: String,
    predicate: LegoSet => Boolean,
    limit: Int
  ): List[LegoSet] = {
    Using(Source.fromFile(filename)) { source =>
      source.getLines().flatMap(parseLegoSet).filter(predicate).take(limit).toList
    }.get

  }

  def readLegoSetStreams(
    filename: String,
    predicate: LegoSet => Boolean,
    limit: Int
  ): IO[List[LegoSet]] = {
    Files[IO]
      .readAll(Path(filename))
      .through(text.utf8.decode)
      .through(text.lines)
      .parEvalMapUnbounded(s => IO(parseLegoSet(s)))
      .unNone
      .filter(predicate)
      .take(limit)
      .compile
      .toList

  }

  override def run: IO[Unit] =
    readLegoSetStreams("sets.csv", _.year > 1980, 1000).flatMap(IO.println(_))

}
