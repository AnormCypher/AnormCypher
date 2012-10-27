package anormcypher

import CypherParser.CypherResultSet

object CypherParser {
  import MayErr._
  import java.util.Date

  type CypherResultSet = Stream[Row]

  def scalar[T](implicit transformer: Column[T]): RowParser[T] = RowParser[T] { row =>
    (for {
      meta <- row.metaData.ms.headOption.toRight(NoColumnsInReturnedResult)
      value <- row.data.headOption.toRight(NoColumnsInReturnedResult)
      result <- transformer(value, meta)
    } yield result).fold(e => Error(e), a => Success(a))
  }

  def flatten[T1, T2, R](implicit f: anormcypher.TupleFlattener[(T1 ~ T2) => R]): ((T1 ~ T2) => R) = f.f

  def str(columnName: String): RowParser[String] = get[String](columnName)(implicitly[anormcypher.Column[String]])

  def bool(columnName: String): RowParser[Boolean] = get[Boolean](columnName)(implicitly[Column[Boolean]])

  def int(columnName: String): RowParser[Int] = get[Int](columnName)(implicitly[Column[Int]])

  def long(columnName: String): RowParser[Long] = get[Long](columnName)(implicitly[Column[Long]])

  def date(columnName: String): RowParser[Date] = get[Date](columnName)(implicitly[Column[Date]])

  def getAliased[T](aliasName: String)(implicit extractor: anormcypher.Column[T]): RowParser[T] = RowParser { row =>
    import MayErr._

    (for {
      meta <- row.metaData.getAliased(aliasName)
        .toRight(ColumnNotFound(aliasName, row.metaData.availableColumns))
      value <- row.getAliased(aliasName)
      result <- extractor(value, MetaDataItem(meta._1, meta._2, meta._3))
    } yield result).fold(e => Error(e), a => Success(a))
  }

  def get[T](columnName: String)(implicit extractor: anormcypher.Column[T]): RowParser[T] = RowParser { row =>
    import MayErr._

    (for {
      meta <- row.metaData.get(columnName)
        .toRight(ColumnNotFound(columnName, row.metaData.availableColumns))
      value <- row.get1(columnName)
      result <- extractor(value, MetaDataItem(meta._1, meta._2, meta._3))
    } yield result).fold(e => Error(e), a => Success(a))
  }

  def contains[TT: Column, T <: TT](columnName: String, t: T): RowParser[Unit] =
    get[TT](columnName)(implicitly[Column[TT]])
      .collect("Row doesn't contain a column: " + columnName + " with value " + t) { case a if a == t => Unit }

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

object RowParser {

  def apply[A](f: Row => CypherResult[A]): RowParser[A] = new RowParser[A] {

    def apply(row: Row): CypherResult[A] = f(row)

  }

}

trait RowParser[+A] extends (Row => CypherResult[A]) {

  parent =>

  def map[B](f: A => B): RowParser[B] = RowParser(parent.andThen(_.map(f)))

  def collect[B](otherwise: String)(f: PartialFunction[A, B]): RowParser[B] = RowParser(row => parent(row).flatMap(a => if (f.isDefinedAt(a)) Success(f(a)) else Error(CypherMappingError(otherwise))))

  def flatMap[B](k: A => RowParser[B]): RowParser[B] = RowParser(row => parent(row).flatMap(a => k(a)(row)))

  def ~[B](p: RowParser[B]): RowParser[A ~ B] = RowParser(row => parent(row).flatMap(a => p(row).map(new ~(a, _))))

  def ~>[B](p: RowParser[B]): RowParser[B] = RowParser(row => parent(row).flatMap(a => p(row)))

  def <~[B](p: RowParser[B]): RowParser[A] = parent.~(p).map(_._1)

  def |[B >: A](p: RowParser[B]): RowParser[B] = RowParser { row =>
    parent(row) match {

      case Error(_) => p(row)

      case a => a

    }
  }

  def ? : RowParser[Option[A]] = RowParser { row =>
    parent(row) match {
      case Success(a) => Success(Some(a))
      case Error(_) => Success(None)
    }
  }

  def >>[B](f: A => RowParser[B]): RowParser[B] = flatMap(f)

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

  def list[A](p: RowParser[A]): CypherResultSetParser[List[A]] = {
    // Performance note: sequence produces a List in reverse order, since appending to a
    // List is an O(n) operation, and this is done n times, yielding O(n2) just to convert the
    // result set to a List.  Prepending is O(1), so we use prepend, and then reverse the result
    // in the map function below.
    @scala.annotation.tailrec
    def sequence(results: CypherResult[List[A]], rows: Stream[Row]): CypherResult[List[A]] = {

      (results, rows) match {

        case (Success(rs), row #:: tail) => sequence(p(row).map(_ +: rs), tail)

        case (r, _) => r

      }

    }

    CypherResultSetParser { rows => sequence(Success(List()), rows).map(_.reverse) }
  }

  def nonEmptyList[A](p: RowParser[A]): CypherResultSetParser[List[A]] = CypherResultSetParser(rows => if (rows.isEmpty) Error(CypherMappingError("Empty Result Set")) else list(p)(rows))

  def single[A](p: RowParser[A]): CypherResultSetParser[A] = CypherResultSetParser {
    case head #:: Stream.Empty => p(head)
    case Stream.Empty => Error(CypherMappingError("No rows when expecting a single one"))
    case _ => Error(CypherMappingError("too many rows when expecting a single one"))

  }

  def singleOpt[A](p: RowParser[A]): CypherResultSetParser[Option[A]] = CypherResultSetParser {
    case head #:: Stream.Empty => p.map(Some(_))(head)
    case Stream.Empty => Success(None)
    case _ => Error(CypherMappingError("too many rows when expecting a single one"))
  }

}
