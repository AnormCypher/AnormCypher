package org.anormcypher

class CypherTransactionSpec extends BaseAnormCypherSpec {

  val a1 = Cypher("CREATE (n {tag:'transactiontest', name:'a1'})")
  val a2 = Cypher("CREATE (n {tag:'transactiontest', name:'a2'})")
  val b1 = Cypher("CREATE (n {tag:'transactiontest', name:'b1'})")
  val b2 = Cypher("CREATE (n {tag:'transactiontest', name:'b2'})")
  val badQuery = Cypher("CREATE (n {tag:'transactiontest', name:'b1')")
  val t1 = CypherTransaction("ta", Seq(a1, a2))
  val t2 = CypherTransaction("tb", Seq(b1, b2))
  val nodeNamesQuery = """
    START n = node(*) WHERE n.tag = 'transactiontest'
    RETURN n.name AS name, n ORDER BY name
  """
  override def afterEach: Unit = {
    // delete the test data
    Cypher("""MATCH (n) WHERE n.tag = 'transactiontest'
      OPTIONAL MATCH (n)-[r]-() DELETE n, r""")()
  }

  // scalastyle:off multiple.string.literals
  "CypherTransaction" should "be able to create nodes" in {
    t1.commit()
    val results = Cypher(nodeNamesQuery)().map { row =>
      row[String]("name")
    }.toList
    results should be (Seq("a1", "a2"))
  }

  it should "fail if any one query in the transaction is bad" in {
    CypherTransaction("rollback", Seq(a1, a2, badQuery)).commit() should be (false)
    val results = Cypher("""
      START n = node(*) WHERE n.tag = 'transactiontest'
      RETURN n.name AS name, n ORDER BY name""")().map { row =>
      row[String]("name")
    }.toList
    results should be ('isEmpty)
  }

  it should "do batch processing" in {
    val batch = CypherBatch(Seq(t1, t2))
    batch.execute() should be (true)
    val results = Cypher(nodeNamesQuery)().map { row =>
      row[String]("name")
    }.toList
    results should be (Seq("a1", "a2", "b1", "b2"))
  }

}
