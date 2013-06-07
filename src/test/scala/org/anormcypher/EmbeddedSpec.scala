package org.anormcypher

import CypherEmbedded._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time._

class EmbeddedSpec extends CommonTest with BeforeAndAfterAll with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout =  Span(2, Seconds), interval = Span(5, Millis))

  override def beforeAll = {
    createDb("./anormcypher-testdb", 
      Map("read_only" -> "false"))
  }

  override def beforeEach = {
    waitIO {
      cypher("start n=node(*) match n-[r?]-() delete n,r").execute
    }
  }

  override def afterAll = {
    shutdown
    // delete folder?
  }

  "Embedded" should "be able to query a fresh db" in {
    val res = cypher("start n=node(*) return count(*)")()
    whenReady(res) { r =>
      r.toList.head("count(*)") should equal(0)
    }
  }

  it should "be able to create a new node and query it" in {
    val f1 = cypher("create (n {x:1}) return n")()
    whenReady(f1) { r1 =>
      println(r1.toList.head)
      val f2 = cypher("start n=node(*) return count(*)")()
      whenReady(f2) { r2 =>
        r2.toList.head("count(*)") should equal(1)
      }
    }
  }
}
