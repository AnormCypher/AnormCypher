package org.anormcypher

abstract class CypherRequestError

case class TypeDoesNotMatch(message: String) extends CypherRequestError

case class InnerTypeDoesNotMatch(message: String) extends CypherRequestError

case class UnexpectedNullableFound(on: String) extends CypherRequestError

case object NoColumnsInReturnedResult extends CypherRequestError

case class CypherMappingError(msg: String) extends CypherRequestError

case class MockRow(metaData: MetaData, data: List[Any]) extends CypherRow
