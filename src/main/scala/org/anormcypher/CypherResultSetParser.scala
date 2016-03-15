package org.anormcypher

import CypherParser.CypherResultSet

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
    def sequence(results: CypherResult[List[A]], rows: Seq[CypherRow]): CypherResult[List[A]] = {
      (results, rows) match {
        case (Success(rs), Seq(row, tail @ _ *)) => sequence(p(row).map(_ +: rs), tail)
        case (r, _) => r
      }
    }

    CypherResultSetParser { rows => sequence(Success(List()), rows).map(_.reverse) }
  }

  def nonEmptyList[A](p: CypherRowParser[A]): CypherResultSetParser[List[A]] = CypherResultSetParser(rows => if (rows.isEmpty) Error(CypherMappingError("Empty Result Set")) else list(p)(rows))

  def single[A](p: CypherRowParser[A]): CypherResultSetParser[A] = CypherResultSetParser {
    case Seq(head) => p(head)
    case Seq() => Error(CypherMappingError("No rows when expecting a single one"))
    case _ => Error(CypherMappingError("too many rows when expecting a single one"))
  }

  def singleOpt[A](p: CypherRowParser[A]): CypherResultSetParser[Option[A]] = CypherResultSetParser {
    case Seq(head) => p.map(Some(_))(head)
    case Seq() => Success(None)
    case _ => Error(CypherMappingError("too many rows when expecting a single one"))
  }

}
