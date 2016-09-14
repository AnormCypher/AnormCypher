package org.anormcypher

import akka.stream._, scaladsl._, stage._
import akka.util.ByteString
import com.sorrentocorp.akka.stream._
import play.api.libs.json._

import scala.util._, control.NonFatal

class CypherResultRowFraming extends GraphStage[FlowShape[Neo4jRespToken, CypherResultRow]] {
  override protected def initialAttributes: Attributes = Attributes.name("CypherResultRowFraming.scanner")

  val in = Inlet[Neo4jRespToken]("CypherResultRowFraming.in")
  val out = Outlet[CypherResultRow]("CypherResultRowFraming.out")
  override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) with InHandler with OutHandler {
    setHandlers(in, out, this)

    override def onPush(): Unit = {
      val curr = grab(in)
      curr match {
        case EmptyResult =>

        case ResultColumn(meta) =>
          meta.utf8String

        case DataRow(row) =>
          row.utf8String

        case ErrorObj(errors) =>
          errors.utf8String
      }

      tryPopBuffer()
    }

    override def onPull(): Unit = {
      tryPopBuffer()
    }

    override def onUpstreamFinish(): Unit = {
      lexer.poll match {
        case Some(token) => emit(out, token)
        case None => completeStage()
      }
    }

    def tryPopBuffer(): Unit = {
      try lexer.poll match {
        case Some(token) => emit(out, token)
        case None => if (isClosed(in)) completeStage() else pull(in)
      } catch {
        case NonFatal(ex) => failStage(ex)
      }
    }
  }
}

object Neo4jStream {
  def parse[T](source: Source[ByteString, T])(implicit mat: Materializer): Source[CypherResultRow, T] =
    source.via(new Neo4jRespFraming).via(new CypherResultRowFraming)
}
