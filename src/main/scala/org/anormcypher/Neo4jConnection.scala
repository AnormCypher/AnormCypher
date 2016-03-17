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

  trait Neo4jTransaction {
    def txId: String
    def connection: Neo4jConnection
    // Both commit and rollback are blocking operations because a callback api is not as clear
    def commit(implicit ec: ExecutionContext): Unit
    def rollback(implicit ec: ExecutionContext): Unit
  }

  private[anormcypher] def beginTx(implicit ec: ExecutionContext): Future[Neo4jTransaction]

  /** Loan Pattern encapsulates transaction lifecycle */
  def withTx[A](code: (Neo4jConnection, ExecutionContext) => A)(implicit ec: ExecutionContext): Future[A] =
    for {
      tx <- beginTx(ec)
    } yield try {
      // TODO: deal with code that returns Future
      val r = code(tx.connection, ec)
      tx.commit(ec)
      r
    } catch {
      case e: ControlThrowable => tx.commit(ec); throw e
      case e: Throwable =>      tx.rollback(ec); throw e
    }
}
