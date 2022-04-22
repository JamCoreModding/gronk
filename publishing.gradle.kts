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

configure<com.matthewprenger.cursegradle.CurseExtension> {
    if (secrets["curseforge_api_key"] != null) {
        apiKey = secrets["curseforge_api_key"] as String
    } else if (System.getenv()["CURSEFORGE_API_KEY"] != null) {
        apiKey = System.getenv()["CURSEFORGE_API_KEY"]
    } else {
        println("CURSEFORGE_API_KEY not found in secrets.properties or system environment")
    }

    project(closureOf<CurseProject> {
        if (project.properties["curseforge_project_id"] != null) {
            id = project.properties["curseforge_project_id"] as String
        } else {
            println("curseforge_project_id not found in project.properties")
        }

        changelogType = "markdown"

        if (project.rootProject.file("CHANGELOG.md").exists()) {
            changelog = project.rootProject.file("CHANGELOG.md")
        } else {
            println("No CHANGELOG.md found")
            changelog = "No changelog provided"
        }

        if (project.properties["release_channel"] != null) {
            releaseType = project.properties["release_channel"] as String
        } else {
            println("release_channel not found in project.properties, defaulting to 'release'")
            releaseType = "release"
        }

        val tagName: String

        if (project.properties["modVersion"] != null) {
            tagName = project.properties["modVersion"] as String
        } else if (project.properties["mod_version"] != null) {
            tagName = project.properties["mod_version"] as String
        } else {
            println("version not found in gradle.properties, defaulting to project.version")
            tagName = project.version.toString()
        }

        mainArtifact(tasks.get("remapJar"))

        if (project.properties["release_name"] != null) {
            mainArtifact.displayName = project.properties["release_name"] as String
        } else {
            println("release_name not found in gradle.properties, defaulting to tag name")
            mainArtifact.displayName = "V${tagName.toUpperCase()}"
        }


        afterEvaluate {
            uploadTask.dependsOn("remapJar")
        }

        addGameVersion("Fabric")

        if (project.properties["supported_versions"] != null) {
            val versions = (project.properties["supported_versions"] as String).split(",")
            versions.forEach {
                addGameVersion(it)
            }
        } else if (project.rootProject.file("VERSIONS.md").exists()) {
            project.rootProject.file("VERSIONS.txt").readText().split("\r\n").forEach {
                addGameVersion(it)
            }
        } else {
            println("No versions found, defaulting to 1.18.2")
            addGameVersion("1.18.2")
        }
    })

    options(closureOf<Options> {
        forgeGradleIntegration = false
    })
}

configure<com.github.breadmoirai.githubreleaseplugin.GithubReleaseExtension> {
    if (secrets["github_token"] != null) {
        token(secrets["github_token"] as String)
    } else if (System.getenv()["GITHUB_TOKEN"] != null) {
        token(System.getenv()["GITHUB_TOKEN"])
    } else {
        println("github_token not found in gradle.properties or system environment")
    }

    if (project.properties["github_user"] != null) {
        owner(project.properties["github_user"] as String)
    } else {
        println("github_user not found in gradle.properties, defaulting to JamCoreModding")
        owner("JamCoreModding")
    }

    if (project.properties["github_repo"] != null) {
        repo(project.properties["github_repo"] as String)
    } else {
        println("github_repo not found in gradle.properties")
    }

    val tagName2: String

    if (project.properties["modVersion"] != null) {
        tagName(project.properties["modVersion"] as String)
        tagName2 = project.properties["modVersion"] as String
    } else if (project.properties["mod_version"] != null) {
        tagName(project.properties["mod_version"] as String)
        tagName2 = project.properties["mod_version"] as String
    } else {
        println("version not found in gradle.properties, defaulting to project.version")
        tagName2 = project.version.toString()
    }

    if (project.properties["release_branch"] != null) {
        targetCommitish(project.properties["release_branch"] as String)
    } else {
        println("release_branch not found in gradle.properties, defaulting to 'main'")
    }

    if (project.properties["release_name"] != null) {
        releaseName(project.properties["release_name"] as String)
    } else {
        println("release_name not found in gradle.properties, defaulting to tag name")
        releaseName("V${tagName2.toUpperCase()}")
    }

    if (project.rootProject.file("CHANGELOG.md").exists()) {
        body(project.rootProject.file("CHANGELOG.md").readText())
    } else {
        println("No CHANGELOG.md found")
        body("No changelog provided")
    }

    if (project.properties["release_channel"] != null) {
        prerelease((project.properties["release_channel"] as String) == "release")
    } else {
        println("release_channel not found in project.properties, defaulting to 'release'")
        prerelease(false)
    }

    val libs = project.file("build/libs").listFiles().filter { it.name.endsWith(".jar") }
    val devLibs = project.file("build/devlibs").listFiles().filter { it.name.endsWith(".jar") }

    if (libs.isEmpty() && devLibs.isEmpty()) {
        println("No artifacts found")
    }

    releaseAssets(libs, devLibs)
}

tasks {
    named("build") {
        doFirst {
            println("Cleaning build/libs")
            val libs = project.file("build/libs").listFiles().filter { it.name.endsWith(".jar") }
            libs.forEach {
                it.delete()
            }

            println("Cleaning build/devlibs")
            val devLibs = project.file("build/devlibs").listFiles().filter { it.name.endsWith(".jar") }
            devLibs.forEach {
                it.delete()
            }
        }
    }

    named("publish") {
        dependsOn("curseforge")
        dependsOn("githubRelease")

        doLast {
            println("Cleaning CHANGELOG.md")
            val changelog = project.rootProject.file("CHANGELOG.md")
            println("CHANGELOG.md: \\n ${changelog.readText()}")
            changelog.writeText("")

            println("Cleaning build/libs")
            val libs = project.file("build/libs").listFiles().filter { it.name.endsWith(".jar") }
            libs.forEach {
                it.delete()
            }

            println("Cleaning build/devlibs")
            val devLibs = project.file("build/devlibs").listFiles().filter { it.name.endsWith(".jar") }
            devLibs.forEach {
                it.delete()
            }
        }
    }

    named("githubRelease") {
        dependsOn("jar")
        dependsOn("remapJar")
    }
}

configure<PublishingExtension> {
    publications {
        if (project.hasProperty("publish_to_maven")) {
            create<MavenPublication>("maven") {
                groupId = "io.github.jamalam360"
                artifactId = project.property("archive_base_name") as String

                if (project.properties["modVersion"] != null) {
                    version = project.properties["modVersion"] as String
                } else if (project.properties["mod_version"] != null) {
                    version = project.properties["mod_version"] as String
                } else {
                    println("version not found in gradle.properties")
                }

                from(components["java"])
            }
        }
    }

    repositories {
        if ((secrets["maven_username"] != null && secrets["maven_password"] != null) ||
                (System.getenv()["MAVEN_USERNAME"] != null
                        && System.getenv()["MAVEN_PASSWORD"] != null)
        ) {
        maven {
            name = "JamalamMavenRelease"
            url = uri("https://maven.jamalam.tech/releases")
            credentials {
                if (secrets["maven_username"] != null && secrets["maven_password"] != null) {
                    username = secrets["maven_username"] as String
                    password = secrets["maven_password"] as String
                } else {
                    username = System.getenv()["MAVEN_USERNAME"]!!
                    password = System.getenv()["MAVEN_PASSWORD"]!!
                }
            }
        }
    }
    }
}
