package org.anormcypher.newapi

import org.anormcypher.CommonTest

class NewApiSpec extends CommonTest {

  "New API" should "be able to create simple Cypher query against embedded" in {
    import CypherEmbedded._
    val query = "start n=node(0) return n"
    val q = cypher(query)
    q.query should equal(query)
    q.params should equal(Seq.empty)
    q.serialize should equal(Map(
      "query" → query,
      "params" → Map.empty
    ))
  }

  it should "be able to create parametrized Cypher query against embedded" in {
    import CypherEmbedded._
    val query = "start n=node({id}) return n"
    val q = cypher(query).on("id", 0)
    q.query should equal(query)
    q.params.map(t2 ⇒ t2._1 → t2._2.value) should equal(Seq("id" → 0))
    q.serialize should equal(Map(
      "query" → query,
      "params" → Map(
        "id" → 0
      )
    ))
  }

  it should "be able to create simple Cypher query against rest" in {
    import CypherRest._
    val query = "start n=node(0) return n"
    val q = cypher(query)
    q.query should equal(query)
    q.params should equal(Seq.empty)
    q.serialize should equal(json.obj("query" → query, "params" → json.toJson(json.obj())))

  }

  it should "be able to create parametrized Cypher query against rest" in {
    import CypherRest._
    val query = "start n=node({id}) return n"
    val q = cypher(query).on("id", 0)
    q.query should equal(query)
    q.params.map(t2 ⇒ t2._1 → t2._2.value.as[Int]) should equal(Seq("id" → 0))
    q.serialize should equal(json.obj("query" → query, "params" → json.toJson(json.obj("id" → 0))))
  }
}
