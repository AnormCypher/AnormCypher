package org.anormcypher

import scala.concurrent._, duration._

class Neo4jTransactionSpec extends async.BaseAsyncSpec {
  def beginTx = Await.result(neo4jrest.beginTx, 3.seconds)

  "Neo4jTransaction" should "provide an autocommit Neo4jTransaction in the implicit scope" in {
    // normal implicit scope contains an autocommit
    intercept[UnsupportedOperationException] { implicitly[Neo4jTransaction].commit }
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

  "Neo4jTransaction.withTx" should "replace the autocommit transaction with  an open transaction" in {
    Neo4jTransaction.withTx { implicit tx =>
      tx.txId.matches("\\d+") shouldBe true
    }.futureValue
  }

  it should "execute all statements in transaction" in {
    Neo4jTransaction.withTx { implicit tx =>
      val res1 = Cypher(s"""create (n:${Tag}{name: "n1", level: 1}) return n.name as name, n.level as level """)()
      res1(0)[String]("name") shouldBe "n1"

      val res2 = Cypher(s"""create (n:${Tag}{name: "n2", level: 2}) return n.name as name""")()
      res2(0)[String]("name") shouldBe "n2"

      val res3 = Cypher(s"""match (n1:${Tag}{name: "n1"}), (n2:${Tag}{name: "n2"})
create (n1)-[r:hasChildren]->(n2)""")()
    }.futureValue

    // check that all statements have been executed correctly
    val res = Cypher(s"""match (n1:${Tag}{name: "n1"})-[:hasChildren]->(n2) return n2.name as name, n2.level as level""")()
    res.length shouldBe 1
    res(0)[String]("name") shouldBe "n2"
    res(0)[Int]("level") shouldBe 2
  }

  it should "rollback the transaction if the code block throws an Exception" in {
    val res = Neo4jTransaction.withTx { implicit tx =>
      Cypher(s"""create (:${Tag} {msg: "should not have been created"})""")
      1 / 0 // should throw an exception and cause the transaction to be rolled back
    }
    Await.ready(res, 3.seconds)
    res.value.get shouldBe 'Failure
    // check that the node did not get created
    val created = Cypher(s"match (n:${Tag}) return n")()
    created shouldBe 'Empty
  }

  it should "rollback the transaction if we use a rollback transaction" in {
    val res = Neo4jTestTransaction.withTx { implicit tx =>
      Cypher(s"""create (:${Tag} {msg: "should disappear"})""")
    }
    Await.ready(res, 3.seconds)
    // check that the node did not get created
    val created = Cypher(s"match (n:${Tag}) return n")()
    created shouldBe 'Empty
  }
}
