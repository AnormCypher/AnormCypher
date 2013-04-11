package org.anormcypher.newapi

import language.implicitConversions

trait CypherValue

trait CypherValueConverter[A, B <: CypherValue] extends (A ⇒ B)

trait CypherRequestConverter[A <: CypherValue, B] extends (CypherRequest[A] ⇒ B)

case class CypherRequest[A <: CypherValue](query: String, params: Seq[(String, A)] = Seq.empty) {
  def on[B, C >: A <: CypherValue](pair: (String, B))(implicit f: CypherValueConverter[B, C]) =
    CypherRequest(query, params :+ (pair._1 → f(pair._2)))

  def serialize[B](implicit f: CypherRequestConverter[A, B]) = f(this)
}

trait CypherSupport {
  def cypher(query: String) = new CypherRequest(query)

  implicit def fun2cvConverter[A, B <: CypherValue](f: (A ⇒ B)) = new CypherValueConverter[A, B] {
    def apply(a: A) = f apply a
  }

  implicit def fun2crConverter[A <: CypherValue, B](f: (CypherRequest[A] ⇒ B)) = new CypherRequestConverter[A, B] {
    def apply(a: CypherRequest[A]) = f apply a
  }
}

trait MapSupport {
  self: CypherSupport ⇒

  sealed trait EmbeddedCypherValue extends CypherValue {
    def value: Any
  }

  case class EmbeddedCypherValueImpl[A](underlying: A) extends EmbeddedCypherValue {
    val value = identity(underlying)
  }

  implicit def any2cv[A] = new CypherValueConverter[A, EmbeddedCypherValue] {
    def apply(obj: A) = EmbeddedCypherValueImpl(obj)
  }

  implicit val cr0p2map = new CypherRequestConverter[Nothing, Map[String, Any]] {
    def apply(obj: CypherRequest[Nothing]) = Map(
      "query" → obj.query,
      "params" → Map.empty
    )
  }
  implicit val cr2map: CypherRequestConverter[EmbeddedCypherValue, Map[String, Any]] =
    (obj: CypherRequest[EmbeddedCypherValue]) ⇒ Map(
      "query" → obj.query,
      "params" → obj.params.groupBy(_._1).mapValues(seq ⇒ seq.map(_._2.value).head)
    )
}

object CypherEmbedded extends CypherSupport with MapSupport