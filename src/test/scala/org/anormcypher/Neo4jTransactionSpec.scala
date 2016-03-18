package org.anormcypher

import scala.concurrent._, duration._

class Neo4jTransactionSpec extends async.BaseAsyncSpec {
  def beginTx = Await.result(neo4jrest.beginTx, 3.seconds)

  "Neo4jTransaction" should "provide an autocommit Neo4jTransaction in the implicit scope" in {
    // normal implicit scope contains an autocommit
    intercept[UnsupportedOperationException] { implicitly[Neo4jTransaction].txId }
  }

  it should "provide an open transaction through withTx" in {
    Neo4jTransaction.withTx { implicit tx =>
      tx.txId.matches("\\d+") shouldBe true
    }.futureValue
  }

  "Neo4jConnection.beginTx" should "return a transaction id" in {
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
