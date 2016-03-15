package org.anormcypher

import CypherParser.CypherResultSet

import MayErr.eitherToError
import MayErr.errorToEither

import java.util.Date

object CypherParser {

  type CypherResultSet = Seq[CypherRow]

  def scalar[T](implicit transformer: Column[T]): CypherRowParser[T] = CypherRowParser[T] { row =>
    (for {
      meta <- row.metaData.ms.headOption.toRight(NoColumnsInReturnedResult)
      value <- row.data.headOption.toRight(NoColumnsInReturnedResult)
      result <- transformer(value, meta)
    } yield result).fold(e => Error(e), a => Success(a))
  }

  def flatten[T1, T2, R](implicit f: org.anormcypher.TupleFlattener[(T1 ~ T2) => R]): ((T1 ~ T2) => R) = f.f

  def str(columnName: String): CypherRowParser[String] = get[String](columnName)(implicitly[org.anormcypher.Column[String]])

  def bool(columnName: String): CypherRowParser[Boolean] = get[Boolean](columnName)(implicitly[Column[Boolean]])

  def int(columnName: String): CypherRowParser[Int] = get[Int](columnName)(implicitly[Column[Int]])
  
  def long(columnName: String): CypherRowParser[Long] = get[Long](columnName)(implicitly[Column[Long]])
  
  def node(columnName: String): CypherRowParser[NeoNode] = get[NeoNode](columnName)(implicitly[Column[NeoNode]])

  def relationship(columnName: String): CypherRowParser[NeoRelationship] = get[NeoRelationship](columnName)(implicitly[Column[NeoRelationship]])

  // TODO use JodaTime and auto-convert to dates
  //def date(columnName: String): CypherRowParser[Date] = get[Date](columnName)(implicitly[Column[Date]])

  def get[T](columnName: String)(implicit extractor: org.anormcypher.Column[T]): CypherRowParser[T] = CypherRowParser { row =>
    import MayErr._

    (for {
      meta <- row.metaData.get(columnName)
        .toRight(ColumnNotFound(columnName, row.metaData.availableColumns))
      value <- row.get1(columnName)
      result <- extractor(value, MetaDataItem(meta._1, meta._2, meta._3))
    } yield result).fold(e => Error(e), a => Success(a))
  }

  def contains[TT: Column, T <: TT](columnName: String, t: T): CypherRowParser[Unit] =
    get[TT](columnName)(implicitly[Column[TT]])
      .collect("CypherRow doesn't contain a column: " + columnName + " with value " + t) { case a if a == t => Unit }

}

case class ~[+A, +B](_1: A, _2: B)

case class Success[A](a: A) extends CypherResult[A]

case class Error(msg: CypherRequestError) extends CypherResult[Nothing]
