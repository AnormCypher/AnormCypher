package org.anormcypher

import play.api.libs.iteratee._

class Neo4j3SupportSpec extends BaseStreamSpec {
  "Neo4j-3.0 Support" should "be able to parse null in meta" in {
    val resp = """
    {"results":[{"columns":["n.name"],"data":[{"row":["AnormCypher"],"meta":[null]},{"row":["Test"],"meta":[null]}]}],"errors":[]}
""""
    val f = Neo4jStream.parse(chunking(resp)) |>>> Iteratee.getChunks[CypherResultRow]
    val result = f.futureValue
    result(0).metaData shouldBe MetaData(List(
      MetaDataItem("n.name", false, "String")
    ))

    result.map(_.data) shouldBe Seq(
      Seq("AnormCypher"),
      Seq("Test")
    )
  }

  it should "be able to parse node properties" in {
    val resp = """
    {"results":[{"columns":["n"],"data":[{"row":[{"arr":[1,2,3],"i":1,"anormcyphername":"nprops","arrb":[false,false,true,false],"arrc":["a","b","c"]}],"meta":[{"id":7,"type":"node","deleted":false}]}]}],"errors":[]}
"""
    val f = Neo4jStream.parse(chunking(resp)) 

  }

}
