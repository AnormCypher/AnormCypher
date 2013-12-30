package org.anormcypher.http

import play.api.libs.iteratee._
import org.scalatest._
import scala.concurrent._
import concurrent.AsyncAssertions
import org.scalatest.time.SpanSugar._
import org.anormcypher.CypherRow
import scala.concurrent.ExecutionContext.Implicits.global

class Neo4jHttpTest extends FlatSpec with Matchers with AsyncAssertions {

  "Neo4jHttp" should "connect and get the cypher URL and trans URL" in {
    val conn = Neo4jHttp("http://localhost:7474/db/data")
    conn.cypherURL should be("http://localhost:7474/db/data/cypher")
    conn.transactionURL should be("http://localhost:7474/db/data/transaction")
  }
  
  it should "be able to send a cypher query async" in {
    val conn = Neo4jHttp("http://localhost:7474/db/data")
    val futureEnumerator = conn.Cypher("return 1")()
    val w = new Waiter

    futureEnumerator.map{enum =>
      enum.apply(Iteratee.foreach { row =>
        println("we're in the foreach/row of the enumerator: "+row)
        w( row.get("1") should be(1))
        w.dismiss()
      })
    }

    w.await(timeout(3000 millis), dismissals(1))
  }

  it should "be able to send a cypher query sync" in {
    val conn = Neo4jHttp("http://localhost:7474/db/data")
    val results = conn.Cypher("return 1").sync()
    results(0)("1") should be(1)
  }
}
