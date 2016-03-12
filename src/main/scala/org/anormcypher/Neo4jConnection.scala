package org.anormcypher

import play.api.libs.iteratee.{Enumerator, Iteratee}
import scala.concurrent.{Future, ExecutionContext}
import scala.util.control.ControlThrowable

/** Neo4j Connection API */
trait Neo4jConnection {
  /** Asynchronous, non-streaming query */
  def sendQuery(cypherStatement: CypherStatement)(implicit ec: ExecutionContext): Future[Seq[CypherResultRow]] =
    if (autocommit)
      streamAutoCommit(cypherStatement)(ec) |>>> Iteratee.getChunks[CypherResultRow]
    else
      ??? // TODO: implement

  /**
   * Asynchornous, streaming (i.e. reactive) query.
   *
   * Because this method is used to deal with large datasets, it is
   * always executed within its own transaction, which is then
   * immediately commited, regardless of the value for `autocommit`.
   * It will also never participate in any existing transaction.
   */
  def streamAutoCommit(stmt: CypherStatement)(implicit ec: ExecutionContext): Enumerator[CypherResultRow]

  /** Transaction API */
  def autocommit: Boolean
  // TODO: implement
  trait Neo4jTransaction {
    def connection: Neo4jConnection
    def commit(implicit ec: ExecutionContext): Unit
    def rollback(implicit ec: ExecutionContext): Unit
  }

  // TODO: implement
  private[anormcypher] def beginTx(implicit ec: ExecutionContext): Neo4jTransaction

  /** Loan Pattern encapsulates transaction lifecycle */
  def withTx[A](code: (Neo4jConnection, ExecutionContext) => A)(implicit ec: ExecutionContext): A = {
    val tx = beginTx(ec)
    try {
      val r = code(tx.connection, ec)
      tx.commit(ec)
      r
    } catch {
      case e: ControlThrowable => tx.commit(ec); throw e
      case e: Throwable =>      tx.rollback(ec); throw e
    }
  }
}
