package org.anormcypher

import scala.concurrent._, duration._

class Neo4jTransactionTest extends BaseAnormCypherSpec {
  def beginTx = Await.result(neo4jrest.beginTx, 3.seconds)

  "Neo4jConnection.beginTx" should "be able to return a transaction id" in {
    beginTx.txId.matches("\\d+") shouldBe true
  }

  "Neo4jTransaction.commit" should "not succeed if it has already been committed" in {
    val tx = beginTx
    tx.commit
    intercept[RuntimeException] { tx.commit }
  }

  it should "not succeed if it has already been rolled back" in {
    val tx = beginTx
    tx.rollback
    intercept[RuntimeException] { tx.commit }
  }
}
