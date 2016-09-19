package org.anormcypher

import akka.NotUsed
import akka.actor._
import akka.stream._, scaladsl._
import akka.util.ByteString
import org.scalatest._, concurrent._, time._

trait BaseStreamSpec extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val system = ActorSystem("anormcypher-stream-test")
  implicit val materializer = ActorMaterializer()
  implicit val ec = materializer.executionContext

  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds))

  override def afterAll = {
    materializer.shutdown()
    system.terminate()
  }

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

  def chunking(whole: String): Source[ByteString, NotUsed] =
    Source.fromIterator(() => randomSplit(whole).map(ByteString(_)).iterator)

  def parse (resp: String): Seq[CypherResultRow] =
    Neo4jStream.parse(chunking(resp)).runWith(Sink.seq).futureValue
}
