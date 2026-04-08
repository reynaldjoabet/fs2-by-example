import scala.concurrent.Future

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import cats.Foldable

object SequenceAndTraverseApp extends IOApp {

  def putStr(str: String): IO[Unit] = IO.delay(println(str))

  val tasks: List[Int]             = (1 to 1000).toList
  def taskExecutor(i: Int): String = s"Executing task $i"

  val runAllTasks: IO[List[Unit]] =
    tasks.traverse(i => putStr(taskExecutor(i))) // traverse is foreach in ZIO

  override def run(args: List[String]): IO[ExitCode] =
    runAllTasks.as(ExitCode.Success)

// alias for fold is combineAll
// map and sequence == traverse
//foldMap maps a user-supplied function over the sequence and combines the results using a Monoid
  val tasks1: List[IO[Int]]           = (1 to 1000).map(IO.pure).toList
  val sequenceAllTasks: IO[List[Int]] = tasks1.sequence
  val printTaskSequence               = sequenceAllTasks.map(_.mkString(", ")).flatMap(putStr)

  val fold = Foldable[List].fold(List(1, 2, 3, 45, 5))

  // we can use foldMap to convert each Int to a String and concatenate them:
//foldLeft iterates through the list from left to right, accumulating elements in that order.

//This also means that when processing the two operands to the combining function, the accumulator is the argument on the left:
  case class Person(name: String, sex: String)

  val persons = List(
    Person("Thomas", "male"),
    Person("Sowell", "male"),
    Person("Liz", "female")
  )

  val foldedList = persons.foldLeft(List[String]()) { (accumulator, person) =>
    val title = person.sex match {
      case "male"   => "Mr."
      case "female" => "Ms."
    }
    accumulator :+ s"$title ${person.name}"
  }

//foldRight, on the other hand, iterates through the list from right to left, accumulating elements in that order. Conversely, this means that our accumulator will be the operand on the right in each iteration:

  val foldedList1 = persons.foldRight(List[String]()) { (person, accumulator) =>
    val title = person.sex match {
      case "male"   => "Mr."
      case "female" => "Ms."
    }
    accumulator :+ s"$title ${person.name}"
  }

  assert(foldedList1 == List("Ms. Liz", "Mr. Sowell", "Mr. Thomas"))

  val foldMap = Foldable[List].foldMap(List(1, 2, 3, 45, 5))(_.toString())

}
