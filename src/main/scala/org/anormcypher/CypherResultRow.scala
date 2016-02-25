package org.anormcypher

case class CypherResultRow(
    metaData: MetaData,
    data: List[Any]) extends CypherRow {

  override def toString(): String = {
    s"CypherResultRow(${metaData.
      ms.
      zip(data).
      map(t => "'" + t._1.column + "':" + t._2 + " as " + t._1.clazz).
      mkString(", ")})"
  }

}
