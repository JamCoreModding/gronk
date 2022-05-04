import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.Options
import java.util.*

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }

    dependencies {
        classpath("gradle.plugin.com.matthewprenger:CurseGradle:1.4.0")
        classpath("com.github.breadmoirai:github-release:2.3.7")
    }
}

apply<com.matthewprenger.cursegradle.CurseGradlePlugin>()
apply<com.github.breadmoirai.githubreleaseplugin.GithubReleasePlugin>()
apply(plugin = "maven-publish")

val secrets = Properties()

if (project.rootProject.file("secrets.properties").exists()) {
    secrets.load(project.rootProject.file("secrets.properties").inputStream())
}

fun getProperty(name: String): String? {
    return project.property[name]
}

fun getSecret(name: String): String? {
    return secrets[name] ?: System.getenv()[name.toUpperCase()]
}

fun isCurseforgeEnabled(): Boolean {
    return getSecret("curseforge_api_key") != null && getProperty("curseforge_project_id") != null && getProperty("supported_versions") != null
}

fun isGithubEnabled(): Boolean {
    return getSecret("github_token") != null && getProperty("github_repo") != null
}

if (isCurseforgeEnabled()) {
    configure<com.matthewprenger.cursegradle.CurseExtension> {
        apiKey = getSecret("curseforge_api_key")!!

        project(closureOf<CurseProject> {
            id = getProperty("curseforge_project_id")!!
            changelogType = "markdown"
            releaseType = "release"
            changelog = project.rootProject.file("CHANGELOG.md")
            tagName = getProperty("mod_version")!!
            mainArtifact.displayName = getProperty("release_name")!!
            mainArtifact(tasks.get("remapJar"))

            afterEvaluate {
                uploadTask.dependsOn("remapJar")
            }

            addGameVersion("Fabric")
            val versions = getProperty("supported_versions")!!.split(",")
            versions.forEach {
                addGameVersion(it)
            }
        })

        options(closureOf<Options> {
            forgeGradleIntegration = false
        })
    }
}

if (isGithubEnabled()) {
    configure<com.github.breadmoirai.githubreleaseplugin.GithubReleaseExtension> {
        token(getSecret("github_token")!!)
        owner(getProperty("github_user")!!)
        repo(getProperty("github_user")!!)
        tagName(getProperty("mod_version")!!)
        releaseName(getProperty("release_name")!!)
        body(project.rootProject.file("CHANGELOG.md").readText())
        prerelease(false)

        if (getProperty("release_branch") != null) {
            targetCommitish(getProperty("release_branch")!!)
        } else {
            targetCommitish("main")
        }

        val libsDir = project.file("build/libs")
        val devLibsDir = project.file("build/devLibs")

        if (libsDir.exists() && devLibsDir.exists()) {
            val libs = libsDir.listFiles().filter { it.name.endsWith(".jar") }
            val devLibs = devLibsDir.listFiles().filter { it.name.endsWith(".jar") }
            releaseAssets(libs, devLibs)
        }
    }
}

tasks {
    named("publish") {
        if (isCurseforgeEnabled()) {
            dependsOn("curseforge")
        }

        if (isGithubEnabled()) {
            dependsOn("githubRelease")
        }

        doLast {
            val changelog = project.rootProject.file("CHANGELOG.md")
            val changelogTemplate = project.rootProject.file("CHANGELOG_TEMPLATE.md")

            if (changelogTemplate.exists()) {
                changelog.writeText(changelogTemplate.readText())
            } else {
                changelog.writeText("")
            }

            val libs = project.file("build/libs").listFiles().filter { it.name.endsWith(".jar") }
            libs.forEach {
                it.delete()
            }

            val devLibs = project.file("build/devlibs").listFiles().filter { it.name.endsWith(".jar") }
            devLibs.forEach {
                it.delete()
            }
        }
    }

    if (isGithubEnabled()) {
        named("githubRelease") {
            dependsOn("jar")
            dependsOn("remapJar")
        }
    }
}

configure<PublishingExtension> {
    val enableMaven = getProperty("publish_to_maven") == "true"

    publications {
        if (enableMaven) {
            create<MavenPublication>("maven") {
                groupId = "io.github.jamalam360"
                artifactId = project.property("archive_base_name") as String

                if (project.properties["mod_version"] != null) {
                    version = project.properties["mod_version"] as String
                } else {
                    println("version not found in gradle.properties")
                }

                from(components["java"])
            }
        }
    }

    repositories {
        if (enableMaven &&
            (getSecret("maven_username") != null && getSecret("maven_password") != null)
        ) {
            maven {
                name = "JamalamMavenRelease"
                url = uri("https://maven.jamalam.tech/releases")
                credentials {
                    username = getSecret("maven_username")!!
                    password = getSecret("maven_password")!!
                }
            }
        }
    }
}
