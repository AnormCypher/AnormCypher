package org.anormcypher

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
