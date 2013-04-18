package org.anormcypher.newapi

import scala.reflect.ClassTag

trait MapSupport {

  sealed trait IdentityValue extends Value {
    def underlying: Any
  }

  implicit def any2identityValue[A: ClassTag] = new ValueConverter[A, IdentityValue] {
    val map = (a: A) ⇒ new IdentityValue {
      val underlying = identity(a)
    }
    val comap = (identityValue: IdentityValue) => identityValue.underlying match {
      case x: A ⇒ Some(x)
      case _ ⇒ None
    }
  }
}