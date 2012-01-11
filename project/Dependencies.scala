package rewire

import sbt._
import Keys._



case class MyDependencyInfo(project: ProjectRef,
                            name: String, 
                            organization: String, 
                            version: String, 
                            module: ModuleID,
                            dependencies: Seq[ModuleID] = Seq())


case class MyDependencyActions(info: MyDependencyInfo,
                               addProjectDependency: Seq[ProjectRef] = Nil,
                               removeLibraryDependency: Seq[ModuleID] = Nil)

/** A trait for use in builds that can rewire local dependencies to be local. */
trait DependencyAnalysis {
  /** Hashes a project dependency to just contain organization and name. */
  private def hashInfo(d: MyDependencyInfo) = d.organization + ":" + d.name
  /** Hashes a module ID to just contain organization and name. */
  private def hashModule(o: ModuleID) = o.organization + ":" + o.name

  /** Pulls the name/organization/version for each project in the CEL build */
  private def getProjectInfos(extracted: Extracted, refs: Iterable[ProjectRef]) =
    (Vector[MyDependencyInfo]() /: refs) { (dependencies, ref) =>
      dependencies :+ MyDependencyInfo(
        ref,
        extracted.get(Keys.name in ref),
        extracted.get(Keys.organization in ref),
        extracted.get(Keys.version in ref),
        extracted.get(Keys.projectID in ref),
        extracted.get(Keys.libraryDependencies in ref))
    }

  /** Figures out which libraryDependencies on a project should be moved to project dependencies. */
  private def analyseDependencies(results: Vector[MyDependencyInfo]): Seq[MyDependencyActions] = {
    val lookUp = (Map[String, MyDependencyInfo]() /: results) { (m, value) =>
       m + (hashInfo(value) -> value)
    }
    (results map { value =>
      val changes = for { dep <- value.dependencies
        proj <- lookUp.get(hashModule(dep))
      } yield (proj.project, dep)
      MyDependencyActions(value, changes map (_._1), changes map (_._2))
    } 
    filterNot (_.addProjectDependency.isEmpty) 
    filterNot (_.removeLibraryDependency.isEmpty))
  }

  /** Removes library dependencies that are actually local, so we don't resolve them with Ivy. */
  private def removeBadLibraryDependencies(actions: Seq[MyDependencyActions])(settings: Seq[Setting[_]]) = {
    def fixLibraryDependencies(s: Setting[_]): Setting[_] = if(s.key.scope.project.isInstanceOf[Select[_]]) s.asInstanceOf[Setting[Seq[ModuleID]]] mapInit { (_, old) =>        
      val Scope(Select(ref), _, _, _) = s.key.scope
      val toRemove = (for {
        proj <- actions
        if proj.info.project == ref
        dep <- proj.removeLibraryDependency
      } yield dep).toSet
      (old filterNot toRemove)
    } else s
    // Now remove bad lib dependencies
    def f(s: Setting[_]): Setting[_] = s.key.key match {
      case libraryDependencies.key => fixLibraryDependencies(s)
      case _ => s
    } 
    settings map f
  }

  /** Creates a sequence of settings to wire project dependencies between disparate source repos. */
  private def addLocalProjectDependencies(actions: Seq[MyDependencyActions]): Seq[Setting[_]] = 
     for {
      action <- actions
      dep <- action.addProjectDependency
      p = action.info.project
     } yield (buildDependencies in Global <<= (buildDependencies in Global, thisProjectRef in p, thisProjectRef in dep) { (deps, refA, refB) =>
       // TODO - ensure scope is correct!
       deps.addClasspath(refA, ResolvedClasspathDependency(refB, None))
     })

  /** Modifies a set of settings using the given actions.  This should remove libraryDependencies and add buildDependencies. */
  private def makeRemoteLibsLocalRefs(actions: Seq[MyDependencyActions])(settings: Seq[Setting[_]]): Seq[Setting[_]] =
     removeBadLibraryDependencies(actions)(settings) ++ addLocalProjectDependencies(actions)

  /** Updates the state of an SBT build such that remote projects that have libraryDependencies are modified to be local. */
  def rewireDependencies(state: State): State = {
    val extracted = Project.extract(state)
    import extracted._
    val refs = (session.mergeSettings map (_.key.scope) collect {
      case Scope(Select(p @ ProjectRef(_,_)),_,_,_) => p
    } toSet)
    val deps = getProjectInfos(extracted, refs)
    val actions = analyseDependencies(deps)
    val transformedSettings = makeRemoteLibsLocalRefs(actions)(session.mergeSettings)
    // Now we need to rip into structure and add references to appropriate projects.
    import Load._      
    val newStructure2 = Load.reapply(transformedSettings, structure)
    Project.setProject(session, newStructure2, state)
  }
}


/** This trait provides settings to rewire remote dependencies to be local if remote project source is used. */
trait RewireLocalDepsStartup extends Build with rewire.DependencyAnalysis {
  private lazy val rewired = AttributeKey[Boolean]("local-deps-rewired")
  def rewireCommandName = "rewire-initialize"

  private final def fixState(state: State): State = 
    if(state.get(rewired) getOrElse false) state
    else rewireDependencies(state).put(rewired, true)

  private def initialize = Command.command(rewireCommandName)(fixState(_))

  final def rewireSettings: Seq[Setting[_]] = Seq(
    commands += initialize,
    onLoad in Global <<= (onLoad in Global) ?? idFun[State],
    onLoad in Global <<= (onLoad in Global) apply ( _ andThen (rewireCommandName :: _))
  )
}
