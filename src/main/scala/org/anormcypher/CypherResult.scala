package org.anormcypher

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
