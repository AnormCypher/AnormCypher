package org.anormcypher

import CypherParser.CypherResultSet

object CypherParser {
  import MayErr._
  import java.util.Date

  type CypherResultSet = Stream[CypherRow]

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

trait CypherResult[+A] {

  self =>

  def flatMap[B](k: A => CypherResult[B]): CypherResult[B] = self match {
    case Success(a) => k(a)
    case e @ Error(_) => e
  }

  def map[B](f: A => B): CypherResult[B] = self match {
    case Success(a) => Success(f(a))
    case e @ Error(_) => e
  }

}

case class Success[A](a: A) extends CypherResult[A]

case class Error(msg: CypherRequestError) extends CypherResult[Nothing]

object CypherRowParser {

  def apply[A](f: CypherRow => CypherResult[A]): CypherRowParser[A] = new CypherRowParser[A] {
    def apply(row: CypherRow): CypherResult[A] = f(row)
  }

}

trait CypherRowParser[+A] extends (CypherRow => CypherResult[A]) {

  parent =>

  def map[B](f: A => B): CypherRowParser[B] = CypherRowParser(parent.andThen(_.map(f)))

  def collect[B](otherwise: String)(f: PartialFunction[A, B]): CypherRowParser[B] = CypherRowParser(row => parent(row).flatMap(a => if (f.isDefinedAt(a)) Success(f(a)) else Error(CypherMappingError(otherwise))))

  def flatMap[B](k: A => CypherRowParser[B]): CypherRowParser[B] = CypherRowParser(row => parent(row).flatMap(a => k(a)(row)))

  def ~[B](p: CypherRowParser[B]): CypherRowParser[A ~ B] = CypherRowParser(row => parent(row).flatMap(a => p(row).map(new ~(a, _))))

  def ~>[B](p: CypherRowParser[B]): CypherRowParser[B] = CypherRowParser(row => parent(row).flatMap(a => p(row)))

  def <~[B](p: CypherRowParser[B]): CypherRowParser[A] = parent.~(p).map(_._1)

  def |[B >: A](p: CypherRowParser[B]): CypherRowParser[B] = CypherRowParser { row =>
    parent(row) match {
      case Error(_) => p(row)
      case a => a
    }
  }

  def ? : CypherRowParser[Option[A]] = CypherRowParser { row =>
    parent(row) match {
      case Success(a) => Success(Some(a))
      case Error(_) => Success(None)
    }
  }

  def >>[B](f: A => CypherRowParser[B]): CypherRowParser[B] = flatMap(f)

  def * : CypherResultSetParser[List[A]] = CypherResultSetParser.list(parent)

  def + : CypherResultSetParser[List[A]] = CypherResultSetParser.nonEmptyList(parent)

  def single = CypherResultSetParser.single(parent)

  def singleOpt = CypherResultSetParser.singleOpt(parent)

}

trait CypherResultSetParser[+A] extends (CypherResultSet => CypherResult[A]) {
  parent =>
    def map[B](f: A => B): CypherResultSetParser[B] = CypherResultSetParser(rs => parent(rs).map(f))
}

object CypherResultSetParser {
  def apply[A](f: CypherResultSet => CypherResult[A]): CypherResultSetParser[A] = new CypherResultSetParser[A] { rows =>
    def apply(rows: CypherResultSet): CypherResult[A] = f(rows)
  }

  def list[A](p: CypherRowParser[A]): CypherResultSetParser[List[A]] = {
    // Performance note: sequence produces a List in reverse order, since appending to a
    // List is an O(n) operation, and this is done n times, yielding O(n2) just to convert the
    // result set to a List.  Prepending is O(1), so we use prepend, and then reverse the result
    // in the map function below.
    @scala.annotation.tailrec
    def sequence(results: CypherResult[List[A]], rows: Stream[CypherRow]): CypherResult[List[A]] = {
      (results, rows) match {
        case (Success(rs), row #:: tail) => sequence(p(row).map(_ +: rs), tail)
        case (r, _) => r
      }
    }

    CypherResultSetParser { rows => sequence(Success(List()), rows).map(_.reverse) }
  }

  def nonEmptyList[A](p: CypherRowParser[A]): CypherResultSetParser[List[A]] = CypherResultSetParser(rows => if (rows.isEmpty) Error(CypherMappingError("Empty Result Set")) else list(p)(rows))

  def single[A](p: CypherRowParser[A]): CypherResultSetParser[A] = CypherResultSetParser {
    case head #:: Stream.Empty => p(head)
    case Stream.Empty => Error(CypherMappingError("No rows when expecting a single one"))
    case _ => Error(CypherMappingError("too many rows when expecting a single one"))
  }

  def singleOpt[A](p: CypherRowParser[A]): CypherResultSetParser[Option[A]] = CypherResultSetParser {
    case head #:: Stream.Empty => p.map(Some(_))(head)
    case Stream.Empty => Success(None)
    case _ => Error(CypherMappingError("too many rows when expecting a single one"))
  }

}
