package org.anormcypher.newapi

import scala.reflect.ClassTag

trait MapSupport {
  self: CypherSupport ⇒

  sealed trait IdentityCypherValue extends CypherValue {
    def underlying: Any
  }

  implicit def any2cv[A: ClassTag] = new CypherValueConverter[A, IdentityCypherValue] {
    val map = (a: A) ⇒ new IdentityCypherValue {
      val underlying = identity(a)
    }
    val comap = (ecv: IdentityCypherValue) => ecv.underlying match {
      case x: A ⇒ Some(x)
      case _ ⇒ None
    }
  }

  implicit val cr0p2query = new CypherRequestConverter[Nothing, String] {
    def apply(obj: CypherRequest[Nothing]) = obj.query
  }
  implicit val cr2queryAndParams: CypherRequestConverter[IdentityCypherValue, (String, Map[String, Any])] =
    (obj: CypherRequest[IdentityCypherValue]) ⇒
      obj.query → obj.params.groupBy(_._1).mapValues(seq ⇒ seq.map(_._2.underlying).head
      )
}

object CypherEmbedded extends CypherSupport with MapSupport