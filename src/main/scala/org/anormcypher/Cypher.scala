package org.anormcypher

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object Cypher {

  def apply(cypher: String) = CypherStatement(cypher)

  def as[T](
      parser: CypherResultSetParser[T],
      rs: Future[Seq[CypherResultRow]])
      (implicit ec: ExecutionContext): Future[T] = rs.map { as(parser,_) }

  def as[T](
      parser: CypherResultSetParser[T],
      rs: Seq[CypherResultRow]): T = parser(rs) match {
    case Success(a) => a
    case Error(e) => sys.error(e.toString)
  }

  def parse[T](
      parser: CypherResultSetParser[T],
      rs: Future[Seq[CypherResultRow]])
      (implicit ec: ExecutionContext): Future[T] = rs.map { parse[T](parser,_) }

  def parse[T](
      parser: CypherResultSetParser[T],
      rs: Seq[CypherResultRow]): T = parser(rs) match {
    case Success(a) => a
    case Error(e) => sys.error(e.toString)
  }

}
