import com.jfrog.bintray.gradle.BintrayExtension

plugins {
  id("nebula.maven-publish")
  id("nebula.source-jar")
  id("nebula.nebula-bintray-publishing")
}

plugins.withId("kotlin") {
  tasks.withType<Javadoc> {
    enabled = true
  }
}

publishing {
  publications {
    getByName<MavenPublication>("nebula") {
      pom {
        url.set("https://github.com/justwrote/kjob")
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }
        developers {
          developer {
            id.set("justwrote")
            name.set("Dominik Schmidt")
            email.set("justwrote@gmail.com")
          }
        }
        scm {
          connection.set("scm:git:git://github.com/justwrote/kjob.git")
          developerConnection
            .set("scm:git:ssh://github.com/justwrote/kjob.git")
          url.set("http://github.com/justwrote/kjob/")
        }
      }
    }
  }
}

bintray {
  user = System.getenv("BINTRAY_USER")
  key = System.getenv("BINTRAY_KEY")
  pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
    userOrg = "justwrote"
    repo = "maven"
    websiteUrl = "https://github.com/justwrote/kjob/"
    issueTrackerUrl = "https://github.com/justwrote/kjob/issues"
    vcsUrl = "https://github.com/justwrote/kjob.git"
    setLicenses("Apache-2.0")
    setLabels("kotlin", "kotlin-library", "kotlin-coroutines", "job-scheduler", "job-queue", "mongodb", "queue", "task", "runner", "job", "job-processor")
  })
}
