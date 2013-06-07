package org.anormcypher

class ApiSpec extends CommonTest {

  "API" should "be able to create simple Cypher query against embedded" in {
    import CypherEmbedded._
    val query = "start n=node(0) return n"
    val q = cypher(query)
    val ser: (String, Map[String, Any]) = serialize(q)
    q.query should equal(query)
    q.params should equal(Seq.empty)
    ser should equal(query → Map.empty[String, Any])
  }

  it should "be able to create parametrized Cypher query against embedded" in {
    import CypherEmbedded._
    val query = "start n=node({id}) return n"
    val q = cypher(query).on("id", 0)
    q.query should equal(query)
    q.params.map(t2 ⇒ t2._1 → t2._2.underlying) should equal(Seq("id" → 0))
    serialize(q) should equal(query → Map("id" → 0))
  }

  it should "be able to create simple Cypher query against rest" in {
    import CypherRest._
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
