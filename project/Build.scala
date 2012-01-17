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


  lazy val git = ghProject("sbt-git-plugin")
  //lazy val appengine = RootProject(uri("git://github.com/sbt/sbt-appengine.git"))
  lazy val assembly = ghProject("sbt-assembly")
  lazy val nativePackager = ghProject("sbt-native-packager")
  //lazy val dirtyMoney = ghProject("sbt-dirty-money.git")
  //lazy val twt = ghProject("sbt-twt")
  lazy val gpg = ghProject("xsbt-gpg-plugin")
  lazy val ghpages = ghProject("xsbt-ghpages-plugin", "jsuereth")
  lazy val sbtOneJar = ghProject("sbt-one-jar")

  def ghProject(repo: String, user: String = "sbt") = 
    RootProject(uri("git://github.com/%s/%s.git".format(user, repo)))
}
