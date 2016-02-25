package org.anormcypher

import MayErr.eitherToError
import MayErr.errorToEither

object CypherRow {
  def unapplySeq(row: CypherRow): Option[List[Any]] = Some(row.asList)
}

trait CypherRow {

  protected[anormcypher] val data: List[Any]
  protected[anormcypher] val metaData: MetaData

  lazy val asList = data.zip(metaData.ms.map(_.nullable)).
                        map { i => if (i._2) Option(i._1) else i._1 }

  lazy val asMap: Map[String, Any] =
    metaData.ms.map(_.column).zip(asList).toMap

  def get[A](
      a: String)
      (implicit c: Column[A]): MayErr[CypherRequestError, A] = {
    CypherParser.get(a)(c)(this) match {
      case Success(a) => Right(a)
      case Error(e) => Left(e)
    }
  }

  private def getType(t: String) = t match {
    case "long" => Class.forName("java.lang.Long")
    case "int" => Class.forName("java.lang.Integer")
    case "boolean" => Class.forName("java.lang.Boolean")
    case _ => Class.forName(t)
  }

  private lazy val ColumnsDictionary: Map[String, Any] =
    metaData.ms.map(_.column.toUpperCase).zip(data).toMap

  private[anormcypher] def get1(
      a: String): MayErr[CypherRequestError, Any] = {
    for {
      meta <- metaData.
                get(a).
                toRight(ColumnNotFound(a, metaData.availableColumns));
      (column, nullable, clazz) = meta;
      result <- ColumnsDictionary.
                  get(column.toUpperCase).
                  toRight(ColumnNotFound(column, metaData.availableColumns))
    } yield result
  }

  def apply[B](a: String)(implicit c: Column[B]): B = get[B](a)(c).get

}
