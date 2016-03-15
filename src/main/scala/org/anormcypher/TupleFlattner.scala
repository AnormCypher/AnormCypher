package org.anormcypher

case class TupleFlattener[F](f: F)

trait PriorityOne {

  implicit def flattenerTo2[T1, T2]: TupleFlattener[(T1 ~ T2) => (T1, T2)] =
    TupleFlattener[(T1 ~ T2) => (T1, T2)] { case (t1 ~ t2) => (t1, t2) }
}

trait PriorityTwo extends PriorityOne {

  implicit def flattenerTo3[T1, T2, T3]: TupleFlattener[(T1 ~ T2 ~ T3) => (T1, T2, T3)] =
    TupleFlattener[(T1 ~ T2 ~ T3) => (T1, T2, T3)] { case (t1 ~ t2 ~ t3) => (t1, t2, t3) }
}

trait PriorityThree extends PriorityTwo {

  implicit def flattenerTo4[T1, T2, T3, T4]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4) => (T1, T2, T3, T4)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4) => (T1, T2, T3, T4)] { case (t1 ~ t2 ~ t3 ~ t4) => (t1, t2, t3, t4) }
}

trait PriorityFour extends PriorityThree {

  implicit def flattenerTo5[T1, T2, T3, T4, T5]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5) => (T1, T2, T3, T4, T5)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5) => (T1, T2, T3, T4, T5)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5) => (t1, t2, t3, t4, t5) }
}

trait PriorityFive extends PriorityFour {

  implicit def flattenerTo6[T1, T2, T3, T4, T5, T6]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6) => (T1, T2, T3, T4, T5, T6)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6) => (T1, T2, T3, T4, T5, T6)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6) => (t1, t2, t3, t4, t5, t6) }
}

trait PrioritySix extends PriorityFive {
  implicit def flattenerTo7[T1, T2, T3, T4, T5, T6, T7]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7) => (T1, T2, T3, T4, T5, T6, T7)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7) => (T1, T2, T3, T4, T5, T6, T7)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7) => (t1, t2, t3, t4, t5, t6, t7) }
}

trait PrioritySeven extends PrioritySix {
  implicit def flattenerTo8[T1, T2, T3, T4, T5, T6, T7, T8]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8) => (T1, T2, T3, T4, T5, T6, T7, T8)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8) => (T1, T2, T3, T4, T5, T6, T7, T8)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8) => (t1, t2, t3, t4, t5, t6, t7, t8) }
}

trait PriorityEight extends PrioritySeven {

  implicit def flattenerTo9[T1, T2, T3, T4, T5, T6, T7, T8, T9]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9) => (T1, T2, T3, T4, T5, T6, T7, T8, T9)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9) => (T1, T2, T3, T4, T5, T6, T7, T8, T9)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 ~ t9) => (t1, t2, t3, t4, t5, t6, t7, t8, t9) }
}

trait PriorityNine extends PriorityEight {

  implicit def flattenerTo10[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 ~ t9 ~ t10) => (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10) }
}

object TupleFlattener extends PriorityNine {

  implicit def flattenerTo11[T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11]: TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10 ~ T11) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] =
    TupleFlattener[(T1 ~ T2 ~ T3 ~ T4 ~ T5 ~ T6 ~ T7 ~ T8 ~ T9 ~ T10 ~ T11) => (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11)] { case (t1 ~ t2 ~ t3 ~ t4 ~ t5 ~ t6 ~ t7 ~ t8 ~ t9 ~ t10 ~ t11) => (t1, t2, t3, t4, t5, t6, t7, t8, t9, t10, t11) }
}
