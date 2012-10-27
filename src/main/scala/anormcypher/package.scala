package object anormcypher {

  //implicit def cypherToSimple(cypher: CypherQuery): SimpleCypher[CypherRow] = cypher.asSimple

  def CYPHER(stmt: String) = Cypher.cypher(stmt)

}
