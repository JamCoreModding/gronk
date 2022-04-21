import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.Options
import java.util.*
import org.gradle.api.file.ConfigurableFileCollection

plugins {
    id("com.matthewprenger.cursegradle") version "1.4.0"
    id("com.github.breadmoirai.github-release") version "2.3.7"
}

val secrets = Properties()

if (project.rootProject.file("secrets.properties").exists()) {
    secrets.load(project.rootProject.file("secrets.properties").inputStream())
}

curseforge {
    if (secrets["curseforge_api_key"] != null) {
        apiKey = secrets["curseforge_api_key"] as String
    } else if (System.getenv()["CURSEFORGE_API_KEY"] != null) {
        apiKey = System.getenv()["CURSEFORGE_API_KEY"]
    } else {
        println("CURSEFORGE_API_KEY not found in local.properties or system environment")
        return
    }

    project(closureOf<CurseProject> {
        if (project.properties["curseforge_project_id"] != null) {
            id = project.properties["curseforge_project_id"] as String
        } else {
            println("curseforge_project_id not found in project.properties")
            return
        }

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

        mainArtifact(tasks.get("remapJar"))

        afterEvaluate {
            uploadTask.dependsOn("remapJar")
        }

        addGameVersion("Fabric")

        if (project.properties["versions"] != null) {
            val versions = (project.properties["versions"] as String).split(",")
            versions.forEach {
                addGameVersion(it)
            }
        } else if (project.rootProject.file("CHANGELOG.md").exists()) {
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
        debug = true
    })
}

githubRelease {
    if (secrets["github_token"] != null) {
        token = secrets["github_token"] as String
    } else if (System.getenv()["GITHUB_TOKEN"] != null) {
        token = System.getenv()["GITHUB_TOKEN"]
    } else {
        println("github_token not found in gradle.properties or system environment")
        return
    }

    if (project.properties["github_user"] != null) {
        user = project.properties["github_user"] as String
    } else {
        println("github_user not found in gradle.properties, defaulting to JamCoreModding")
        user = "JamCoreModding"
    }

    if (project.properties["github_repo"] != null) {
        repo = project.properties["github_repo"] as String
    } else {
        println("github_repo not found in gradle.properties")
        return
    }

    if (project.properties["version"] != null) {
        tagName = project.properties["version"] as String
    } else if (project.properties["modVersion"] != null) {
        tagName = project.properties["modVersion"] as String
    } else if (project.properties["mod_version"] != null) {
        tagName = project.properties["mod_version"] as String
    } else {
        println("version not found in gradle.properties, defaulting to project.version")
    }

    if (project.properties["release_branch"] != null) {
        targetCommitish = project.properties["release_branch"] as String
    } else {
        println("release_branch not found in gradle.properties, defaulting to 'main'")
    }

    if (project.properties["release_name"] != null) {
        releaseName = project.properties["release_name"] as String
    } else {
        println("release_name not found in gradle.properties, defaulting to tag name")
        releaseName = tagName.toUpperCase()
    }

    if (project.rootProject.file("CHANGELOG.md").exists()) {
        body = project.rootProject.file("CHANGELOG.md").readText()
    } else {
        println("No CHANGELOG.md found")
        body = "No changelog provided"
    }

    if (project.properties["release_channel"] != null) {
        prerelease = (project.properties["release_channel"] as String) == "release"
    } else {
        println("release_channel not found in project.properties, defaulting to 'release'")
        prerelease = false
    }

    val libs = project.file("build/libs").listFiles().filter { it.name.endsWith(".jar") && it.name.contains(project.version) }
    val devLibs = project.file("build/devlibs").listFiles().filter { it.name.endsWith(".jar") && it.name.contains(project.version) }

    if (libs.isEmpty() && devLibs.isEmpty()) {
        println("No artifacts found")
    }

    val collection = ConfigurableFileCollection()
    releaseAssets = collection.from(libs, devLibs)

    dryRun = true
}

tasks {
    publish {
        dependsOn("curseforge")
        dependsOn("githubRelease")

        doLast {
            println("Cleaning CHANGELOG.md")
            val changelog = project.rootProject.file("CHANGELOG.md")
            println("CHANGELOG.md: \\n ${changelog.readText()}")
            changelog.writeText("")
        }
    }
}
