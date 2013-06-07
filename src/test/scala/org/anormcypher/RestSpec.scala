package org.anormcypher

import CypherRest._

class RestSpec extends CommonTest {

  "Rest" should "be able to create simple Cypher query against rest" in {
    val query = "start n=node(0) return n"
    val q = cypher(query)
    q.query should equal(query)
    q.params should equal(Seq.empty)
    serialize(q) should equal(json.obj("query" → query, "params" → json.toJson(json.obj())))
  }

  it should "be able to create parametrized Cypher query against rest" in {
    import CypherRest._
    val query = "start n=node({id}) return n"
    val q = cypher(query).on("id", 0)
    q.query should equal(query)
    q.params.map(t2 ⇒ t2._1 → t2._2.underlying.as[Int]) should equal(Seq("id" → 0))
    serialize(q) should equal(json.obj("query" → query, "params" → json.toJson(json.obj("id" → 0))))
  }
}
