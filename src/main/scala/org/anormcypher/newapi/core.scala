package org.anormcypher.newapi

import language.higherKinds

trait Value

trait ValueConverter[A, B <: Value] {
  def map: (A ⇒ B)

  def comap: (B ⇒ Option[A])
}

trait Converter[A <: Value, F[_], B] extends (F[A] ⇒ B)