package org.anormcypher

import CypherEmbedded._
import org.scalatest.BeforeAndAfterAll

class EmbeddedSpec extends CommonTest with BeforeAndAfterAll {

  override def beforeAll = {
    createDb("./anormcypher-testdb", 
      Map("read_only" -> "false"))
  }

  override def afterAll = {
    shutdown
    // delete folder?
  }

  "Embedded" should "be able to query a fresh db" in {
    val res = cypher("start n=node(*) return n, count(*)").execute
    res.toList.head("count(*)") should equal(1)
  }

  it should "be able to create a new node and query it" in {
    cypher("create ({x:1})").execute
    val res = cypher("start n=node(*) return n, count(*)").execute
    res.toList.head("count(*)") should equal(2)
  }
}
