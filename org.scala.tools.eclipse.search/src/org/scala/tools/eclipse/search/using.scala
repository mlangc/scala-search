package org.scala.tools.eclipse.search

import scala.util.control.Exception._
import org.scalaide.logging.HasLogger

object using extends HasLogger {
  def apply[T <: java.io.Closeable, R](resource: => T, handlers: Catch[R] = noCatch)(body: T => R): R = {
    handlers {
      val r = resource
      handlers.andFinally(loggingCatcher{ r.close() }).apply{
        body(r)
      }
    }
  }

  def loggingCatcher: Catch[Unit] =
    nonFatalCatch.withApply{ th => logger.debug("Error closing resource", th) }
}