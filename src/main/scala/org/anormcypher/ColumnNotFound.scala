package org.anormcypher

case class ColumnNotFound(
    columnName: String,
    possibilities: List[String])
  extends CypherRequestError {

  override def toString: String = {
    columnName + " not found, available columns : " +
    possibilities.map { p => p.dropWhile(_ == '.') }.
      mkString(", ") // scalastyle:ignore multiple.string.literals
  }
}
