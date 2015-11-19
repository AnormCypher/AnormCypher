package org.anormcypher

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

import play.api.libs.json.Writes
import play.api.libs.json.Json
import play.api.libs.json.JsArray

/**
 * CypherTransaction case class
 *
 * @param name a string identifier for this transaction
 * @param statements a sequence of CypherStatement elements
 * @param timeout a timeout for this transaction, defaults to Duration.Inf
 */
case class CypherTransaction(
    name: String,
    statements: Seq[CypherStatement],
    timeout: Duration = Duration.Inf) {

  /**
   * Executes this transaction and commits it. If any query in this
   * transaction fails, the entire transaction will rollback.
   */
  def commit()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Boolean = {
    var retVal = true
    try {
      // throws an exception on a query that doesn't succeed.
      Await.result(connection.sendTransaction(this, "transaction/commit"),
                   timeout)
    } catch {
      case e: Exception => retVal = false
    }
    retVal
  }

  /**
   * String representation of this transaction.
   */
  override def toString = Json.prettyPrint(Json.toJson(this))

}

/**
 * Companion object for CypherTransaction
 */
object CypherTransaction {

  /**
   * Implicit writes
   *
   * Generates a Json object from a given CypherTransaction object.
   * [[http://neo4j.com/docs/stable/rest-api-transactional.html]]
   */
  implicit val cypherTransactionWrites: Writes[CypherTransaction] = {
    new Writes[CypherTransaction] {
      def writes(trans: CypherTransaction) = Json.obj(
        "statements" -> new JsArray(
            trans.statements.map(x => Json.obj("statement" -> s"$x"))
        )
      )
    }
  }

}
