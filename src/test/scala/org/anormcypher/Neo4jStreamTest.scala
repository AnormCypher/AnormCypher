package org.anormcypher

import org.scalatest._, concurrent._, time._

import play.api.libs.iteratee._
import play.extras.iteratees._
import scala.concurrent.{Await, Future}

class Neo4jStreamTest extends FlatSpec with Matchers with ScalaFutures {

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

  "Neo4jStream.matchString" should "be able to accept empty string to match" in {
    val src = Enumerator("abc", "def", "g")
    val sink = afterMatchString("")
    leftover(src, sink) shouldBe "abcdefg"
  }

  it should "consume matched strings" in {
    val src = Enumerator("abc", "def", "g")
    val sink = afterMatchString("abc")
    leftover(src, sink) shouldBe "defg"
  }

  it should "leave the correct remaineder of the input after a successful match" in {
    val src = Enumerator("abc", "def", "g")
    val sink = afterMatchString("a")
    leftover(src, sink) shouldBe "bcdefg"
  }

  it should "signal unexpected EOF when running out of input before a successful match" in {
    val res = run(Enumerator.empty[String], afterMatchString("ab"))
    res.failed.futureValue.getMessage shouldBe "Premature end of input, asked to match 'ab', matched '', expecting 'ab'"
  }

  it should "be able to handle empty input while matching" in {
    val src = Enumerator((1 to 10) map (_ => "") :_*) andThen Enumerator("abcdefg")
    val sink = afterMatchString("abc")
    leftover(src, sink) shouldBe "defg"
  }

  "Neo4jStream" should "be able to adapt byte array stream to CypherResultRow" in {
    // TODO: use scalacheck to generate different types of neo4j rest responses
    val whole = """
    {"results":[{"columns":["id","name"],"data":[{"row":[1,"Organism"]},{"row":[2,"Gene Expression Role"]},{"row": [3,"Mutation Type"]},{"row": [4,"Gene"]},{"row": [5,"DNA Part"]},{"row": [6,"Plasmid"]},{"row": [7,"Strain"]},{"row": [8,"Mutation"]},{"row": [9,"User"]}]}], "errors":[]}
"""
    val f = Neo4jStream.parse(chunking(whole)) |>>> Iteratee.getChunks[CypherResultRow]
    val result = f.futureValue
    val metadata = result(0).metaData
    metadata shouldBe MetaData(List(
      MetaDataItem("id", false, "String"),
      MetaDataItem("name", false, "String")))

    result.map(_.data) shouldBe Seq(
      Seq(1, "Organism"),
      Seq(2, "Gene Expression Role"),
      Seq(3, "Mutation Type"),
      Seq(4, "Gene"),
      Seq(5, "DNA Part"),
      Seq(6, "Plasmid"),
      Seq(7, "Strain"),
      Seq(8, "Mutation"),
      Seq(9, "User")
    )
  }

  it should "propagate error from bad json format" in {
    val whole = """{property: "something"}"""
    val f = Neo4jStream.parse(chunking(whole)) |>>> Iteratee.getChunks[CypherResultRow]
    Await.ready(f, patienceConfig.timeout)
    f.value.get shouldBe 'Failure
  }

  it should "treat empty array for both results and errors as succesful transaction" in {
    val json = """{"results":[], "errors":[]}"""
    val f = Neo4jStream.parse(chunking(json)) |>>> Iteratee.getChunks[CypherResultRow]
    f.futureValue shouldBe Seq.empty
  }

  it should "extract the 'message' portion from an neo4j error response" in {
    val msg = "monkeys don't twiddle existential thumbs like the Prince of Denmark"
    val json = s"""{"results": [], "errors": [{"code": "Neo.ClientError.Statement.InvalidSyntax", "message": "$msg"}]}"""
    val f = Neo4jStream.parse(chunking(json)) |>>> Iteratee.getChunks[CypherResultRow]
    Await.ready(f, patienceConfig.timeout)
    val res = f.value.get
    res shouldBe 'Failure
    res.failed.get.getMessage shouldBe msg
  }
}
