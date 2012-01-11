import sbt._
import Keys._





// TODO - make it so we can pull more than one project that uses LS-plugin... UGH
object SbtPluginProjects extends Build with rewire.RewireLocalDepsStartup {
  val root = (Project("sbt-plugins", file(".")) settings(rewireSettings:_*) aggregate(
    git, 
    //appengine, 
    assembly, 
    nativePackager, 
    //dirtyMoney, 
    //twt, 
    gpg,
    ghpages))


  lazy val git = RootProject(uri("git://github.com/sbt/sbt-git-plugin.git"))
  //lazy val appengine = RootProject(uri("git://github.com/sbt/sbt-appengine.git"))
  lazy val assembly = RootProject(uri("git://github.com/sbt/sbt-assembly.git"))
  lazy val nativePackager = RootProject(uri("git://github.com/sbt/sbt-native-packager.git"))
  //lazy val dirtyMoney = RootProject(uri("git://github.com/sbt/sbt-dirty-money.git"))
  //lazy val twt = RootProject(uri("git://github.com/sbt/sbt-twt.git"))
  lazy val gpg = RootProject(uri("git://github.com/sbt/xsbt-gpg-plugin.git"))
  lazy val ghpages = RootProject(uri("git://github.com/jsuereth/xsbt-ghpages-plugin.git"))
}
