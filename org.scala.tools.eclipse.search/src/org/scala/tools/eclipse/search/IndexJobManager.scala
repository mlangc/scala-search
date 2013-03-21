package org.scala.tools.eclipse.search

import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.eclipse.core.resources.IProject
import scala.tools.eclipse.logging.HasLogger
import scala.collection.concurrent.TrieMap
import scala.collection.concurrent.Map
import org.scala.tools.eclipse.search.jobs.ProjectIndexJob
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.jobs.Job
import org.scala.tools.eclipse.search.indexing.Index
import org.eclipse.core.runtime.jobs.IJobChangeEvent
import org.eclipse.core.resources.ResourcesPlugin

/**
 * Responsible for keeping track of the various indexing jobs. It uses
 * ProjectChangeObserver to keep track of Resource Events related to
 * projects.
 */
trait IndexJobManager extends Lifecycle with HasLogger {

  this: Index with SourceIndexer =>

  private val lock = new Object

  @volatile var observer: Observing = _

  // Potentially changed by several threads. This class and the ProjectChangeObserver
  private val indexingJobs: Map[IProject, ProjectIndexJob] =
    new TrieMap[IProject, ProjectIndexJob]

  abstract override def startup() = {
    super.startup()
    observer = ProjectChangeObserver(
      onOpen = startIndexing(_),
      onNewScalaProject = startIndexing(_),
      onClose = stopIndexing(_),
      onDelete = project => {
        stopIndexing(project)
        val d = deleteIndex(project)
        if(!d.toOption.getOrElse(false)) {
          logger.debug(s"Failed to delete index for ${project.getName}")
        }
      })
  }

  abstract override def shutdown() = {
    observer.stop
    indexingJobs.keys.foreach(stopIndexing)
    super.shutdown()
  }

  def startIndexing(project: IProject): Unit = lock.synchronized {
    if (!indexingJobs.contains(project)) {
      val jobOpt = createIndexJob(project)
      jobOpt.foreach { job =>
        job.schedule()
        indexingJobs.put(project, job)
      }
    }
  }

  def stopIndexing(project: IProject): Unit = lock.synchronized {
    indexingJobs.get(project).foreach { job =>
      job.cancel()
      indexingJobs.remove(project, job)
    }
  }

  def isIndexing(project: IProject): Boolean = lock.synchronized {
    indexingJobs.contains(project)
  }

  private def createIndexJob(project: IProject): Option[ProjectIndexJob] = {

    val onStopped = (job: ProjectIndexJob) => {
      // If the job, for some reason, stops we want to remove it.
      indexingJobs.remove(project, job)
      ()
    }

    ScalaPlugin.plugin.asScalaProject(project).map { sp =>
      ProjectIndexJob(this, sp, 5000, onStopped)
    }
  }

}