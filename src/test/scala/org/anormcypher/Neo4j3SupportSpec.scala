package org.anormcypher

class Neo4j3SupportSpec extends BaseStreamSpec {
  "Neo4j-3.0 Support" should "be able to parse null in meta" in {
    val resp = """
    {"results":[{"columns":["n.name"],"data":[{"row":["AnormCypher"],"meta":[null]},{"row":["Test"],"meta":[null]}]}],"errors":[]}
""""
    val result = parse(resp)
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
    val result = parse(resp)
    result.size shouldBe 1
    val node = result(0)[NeoNode]("n")
    node.props("anormcyphername") should equal ("nprops")
    node.props("i") should equal (1)
    node.props("arr").asInstanceOf[Seq[Int]] should equal (Vector(1,2,3))
    node.props("arrc").asInstanceOf[Seq[String]] should equal (Vector("a","b","c"))
    node.props("arrb").asInstanceOf[Seq[Boolean]] should equal (Vector(false, false, true, false))
  }

  it should "be able to parse result as collections" in {
    val resp = """
    {"results":[{"columns":["collect(n)"],"data":[{"row":[[{"anormcyphername":"n"},{"anormcyphername":"n2"}]],"meta":[{"id":35,"type":"node","deleted":false},{"id":36,"type":"node","deleted":false}]}]}],"errors":[]}
"""
    val result = parse(resp)
    result.size shouldBe 1
    result(0)[Seq[NeoNode]]("collect(n)").size shouldBe 2
  }
}
