package org.anormcypher

import org.scalatest._, concurrent._, time._

import play.api.libs.iteratee._
import play.extras.iteratees._
import scala.concurrent.Future

trait BaseStreamSpec extends FlatSpec with Matchers with ScalaFutures {
  val rand = scala.util.Random
  def nonZero(upTo: Int) = rand.nextInt(upTo) match {
    case 0 => 1
    case n => n
  }

  def randomSplit(s: String): Seq[String] = {
    def split1(acc: Vector[String], rem: String): Vector[String] = rem.length match {
      case n if n < 5 => acc :+ rem
      case n =>
        val (first, rest) = rem.splitAt(nonZero(n/2))
        split1(acc :+ first, rest)
    }
    split1(Vector[String](), s)
  }

  def chunking(whole: String): Enumerator[Array[Byte]] =
    (randomSplit(whole).map(s => Enumerator(s.getBytes)) foldRight Enumerator.empty[Array[Byte]]) {
      (chunk, ret) => chunk andThen ret
    }

  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds))
  implicit val ec = scala.concurrent.ExecutionContext.global

  def afterMatchString(value: String): Iteratee[CharString, List[String]] =
    for {
      _ <- Neo4jStream.matchString(value)
      r <- Iteratee.getChunks[CharString]
    } yield r.map(_.mkString)

  def leftover(src: Enumerator[String], sink: Iteratee[CharString, List[String]]): String =
    run(src, sink).futureValue.mkString

  def run(src: Enumerator[String], sink: Iteratee[CharString, List[String]]): Future[List[String]] =
    src.map(_.getBytes) &> Encoding.decode() |>>> sink

  def parse (resp: String): Seq[CypherResultRow] =
    (Neo4jStream.parse(chunking(resp)) |>>> Iteratee.getChunks[CypherResultRow]).futureValue
}
