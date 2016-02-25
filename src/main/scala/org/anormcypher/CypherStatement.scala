package org.anormcypher

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt

case class CypherStatement(query: String, params: Map[String, Any] = Map()) {

  def apply()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Seq[CypherResultRow] = Await.result(async(), 30.seconds)

  def async()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Seq[CypherResultRow]] = connection.sendQuery(this)

  def on(args: (String, Any)*): CypherStatement = {
    this.copy(params = params ++ args)
  }

  def execute()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Boolean = {
    var retVal = true // scalastyle:ignore var.local
    try {
      // throws an exception on a query that doesn't succeed.
      apply()
    } catch {
      case e: Exception => retVal = false
    }
    retVal
  }

  def as[T](
      parser: CypherResultSetParser[T])
      (implicit connection: Neo4jREST, ec: ExecutionContext): T = {
    Cypher.as[T](parser, apply())
  }

  def list[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Seq[A] = {
    as(rowParser.*)
  }

  def single[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): A = {
    as(CypherResultSetParser.single(rowParser))
  }

  def singleOpt[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Option[A] = {
    as(CypherResultSetParser.singleOpt(rowParser))
  }

  def parse[T](
      parser: CypherResultSetParser[T])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): T =
    Cypher.parse[T](parser, apply())

  def executeAsync()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Boolean] = {
    val p = Promise[Boolean]()
    async().onComplete { x => p.success(x.isSuccess) }
    p.future
  }

  def asAsync[T](
      parser: CypherResultSetParser[T])
      (implicit connection: Neo4jREST, ec: ExecutionContext): Future[T] = {
    Cypher.as[T](parser, async())
  }

  def listAsync[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Seq[A]] = asAsync(rowParser.*)

  def singleAsync[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[A] = asAsync(CypherResultSetParser.single(rowParser))

  def singleOptAsync[A](
      rowParser: CypherRowParser[A])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext):
    Future[Option[A]] = asAsync(CypherResultSetParser.singleOpt(rowParser))

  def parseAsync[T](
      parser: CypherResultSetParser[T])
      ()
      (implicit connection: Neo4jREST, ec: ExecutionContext): Future[T] = {
    Cypher.parse[T](parser, async())
  }

  override def toString(): String = query
}
