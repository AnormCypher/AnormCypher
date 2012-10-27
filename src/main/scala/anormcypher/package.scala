package object anormcypher {

  implicit def cypherToSimple(cypher: CypherQuery): SimpleCypher[Row] = cypher.asSimple

  def CYPHER(stmt: String) = Cypher.cypher(stmt)

}
