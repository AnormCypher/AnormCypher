package org.anormcypher

import scala.concurrent.{Future, ExecutionContext}
import scala.util.control.ControlThrowable

/** Provides Transaction that rollsback instead of commiting  */
object Neo4jTestTransaction {
  /**
    * Uses the Neo4jConnection in the implicit scope.
    *
    * Client code can shadow this implicit instance by providing its
    * own Neo4jTransaction implementation in the local scope.
    */
  implicit def autocommitNeo4jTransaction(implicit conn: Neo4jConnection): Neo4jTransaction =
  new Neo4jTransaction {
    override def cypher(stmt: CypherStatement)(implicit ec: ExecutionContext) =
      conn.execute(stmt)
    override def cypherStream(stmt: CypherStatement)(implicit ec: ExecutionContext) =
      throw new NotImplementedError("You can not run an autocommit tx from a rollback tx")

    // return a string instead of throwing as it's a legitimate use
    // case for client to query the transaction id for logging
    override val txId = "No transaction id available in rollback transaction"
    override def commit(implicit ec: ExecutionContext) = nosup("Cannot commit a rollback transaction")
    override def rollback(implicit ec: ExecutionContext) = nosup("Rollbacks happen automatically in a rollback transaction")
  }

  /** Loan Pattern encapsulates transaction lifecycle */
  def withTx[A](code: Neo4jTransaction => A)(implicit conn: Neo4jConnection, ec: ExecutionContext): Future[A] =
  for {
    tx <- conn.beginTx
  } yield try {
    val r = code(tx)
    tx.rollback
    r
  } catch {
    case e: ControlThrowable => tx.rollback; throw e
    case e: Throwable =>      tx.rollback; throw e
  }
}
