package org.anormcypher.async

import org.anormcypher._ // scalastyle:ignore underscore.import

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Seconds
import org.scalatest.time.Span

import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee

import scala.concurrent.Await

class Neo4jStreamSpec extends FlatSpec with Matchers with ScalaFutures {

  val rand = scala.util.Random

  def nonZero(upTo: Int): Int = rand.nextInt(upTo) match {
    case 0 => 1
    case n: Int => n
  }

  def randomSplit(s: String): Seq[String] = {
    def split1(acc: Vector[String], rem: String): Vector[String] = rem.length match {
      case n: Int if n < 5 => acc :+ rem
      case n: Int =>
        val (first, rest) = rem.splitAt(nonZero(n/2))
        split1(acc :+ first, rest)
    }
    split1(Vector[String](), s)
  }

  def chunking(whole: String): Enumerator[Array[Byte]] = (randomSplit(whole).map { s =>
    Enumerator(s.getBytes)
  } :\ Enumerator.empty[Array[Byte]]) {
    (chunk, ret) => chunk andThen ret
  }

  implicit override val patienceConfig = PatienceConfig(timeout = Span(3, Seconds))
  implicit val ec = scala.concurrent.ExecutionContext.global

  "Neo4jStream" should "be able to adapt byte array stream to CypherResultRow" in {
    // TODO: use scalacheck to generate different types of neo4j rest responses
    val whole = """{
      "columns":["id","name"],
      "data":[
        [1,"Organism"], [2,"Gene Expression Role"], [3,"Mutation Type"],
        [4,"Gene"],[5,"DNA Part"],[6,"Plasmid"],[7,"Strain"],[8,"Mutation"],[9,"User"]
      ]
    }
    """
    val f = Neo4jStream.parse(chunking(whole)) |>>> Iteratee.getChunks[CypherResultRow]
    val result = f.futureValue
    val metadata = result(0).metaData
    metadata shouldBe MetaData(List(
      MetaDataItem("id", false, "String"), // scalastyle:ignore
      MetaDataItem("name", false, "String")))

    // scalastyle:off magic.number
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
    // scalastyle:on magic.number
  }

  it should "propagate error from bad json format" in {
    val whole = """{property: "something"}"""
    val f = Neo4jStream.parse(chunking(whole)) |>>> Iteratee.getChunks[CypherResultRow]
    Await.ready(f, patienceConfig.timeout)
    f.value.get shouldBe 'Failure
  }

  it should "extract the 'message' portion from an neo4j error response" in {
    val msg = "monkeys don't twiddle existential thumbs like the Prince of Denmark"
    val json = s"""{"exception": "Syntax Exception", "message": "$msg"}"""
    val f = Neo4jStream.errMsg(chunking(json)) |>>> Iteratee.getChunks[String]
    Await.ready(f, patienceConfig.timeout)
    val res = f.value.get
    res shouldBe 'Failure
    res.failed.get.getMessage shouldBe msg
  }
}
