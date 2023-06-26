import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import com.matthewprenger.cursegradle.Options
import java.util.*

buildscript {
    repositories { maven { url = uri("https://plugins.gradle.org/m2/") } }

    dependencies {
        classpath("gradle.plugin.com.matthewprenger:CurseGradle:1.4.0")
        classpath("com.modrinth.minotaur:Minotaur:2.+")
        classpath("com.github.breadmoirai:github-release:2.4.1")
    }
}

apply<com.matthewprenger.cursegradle.CurseGradlePlugin>()

apply<com.modrinth.minotaur.Minotaur>()

apply<com.github.breadmoirai.githubreleaseplugin.GithubReleasePlugin>()

apply(plugin = "maven-publish")

val secrets = Properties()

if (project.rootProject.file("secrets.properties").exists()) {
    secrets.load(project.rootProject.file("secrets.properties").inputStream())
}

fun getGradleProperty(name: String): String? {
    return project.properties[name] as String?
}

fun getGradleSecret(name: String): String? {
    return secrets[name] as? String ?: System.getenv()[name.toUpperCase()]
}

fun isCurseforgeEnabled(): Boolean {
    return getGradleSecret("curseforge_api_key") != null &&
            getGradleProperty("curseforge_project_id") != null &&
            getGradleProperty("supported_versions") != null
}

fun isModrinthEnabled(): Boolean {
    return getGradleSecret("modrinth_api_key") != null &&
            getGradleProperty("modrinth_project_id") != null &&
            getGradleProperty("supported_versions") != null
}

fun isGithubEnabled(): Boolean {
    return getGradleSecret("github_token") != null && getGradleProperty("github_repo") != null
}

if (isCurseforgeEnabled()) {
    println("Curseforge Publishing: ☑")

    configure<com.matthewprenger.cursegradle.CurseExtension> {
        apiKey = getGradleSecret("curseforge_api_key")!!

        project(
                closureOf<CurseProject> {
                    id = getGradleProperty("curseforge_project_id")!!
                    changelogType = "markdown"
                    releaseType =
                            if (getGradleProperty("mod_version")!!.contains("beta")) "beta"
                            else "release"
                    changelog = project.rootProject.file("CHANGELOG.md")
                    mainArtifact(tasks.get("remapJar"))
                    mainArtifact.displayName = getGradleProperty("release_name")!!

                    afterEvaluate { uploadTask.dependsOn("remapJar") }

                    addGameVersion("Quilt")

                    getGradleProperty("supported_versions")!!.split(",").forEach {
                        addGameVersion(it)
                    }

                    if (getGradleProperty("curseforge_required_dependencies") != null ||
                                    getGradleProperty("curseforge_embedded_dependencies") != null ||
                                    getGradleProperty("curseforge_optional_dependencies") != null ||
                                    getGradleProperty("curseforge_tool_dependencies") != null ||
                                    getGradleProperty("curseforge_incompatible_dependencies") !=
                                            null
                    )
                            relations(
                                    closureOf<CurseRelation> {
                                        if (getGradleProperty("curseforge_required_dependencies") !=
                                                        null
                                        ) {
                                            getGradleProperty("curseforge_required_dependencies")!!
                                                    .split(",")
                                                    .forEach { requiredDependency(it) }
                                        }

                                        if (getGradleProperty("curseforge_embedded_dependencies") !=
                                                        null
                                        ) {
                                            getGradleProperty("curseforge_embedded_dependencies")!!
                                                    .split(",")
                                                    .forEach { embeddedLibrary(it) }
                                        }

                                        if (getGradleProperty("curseforge_optional_dependencies") !=
                                                        null
                                        ) {
                                            getGradleProperty("curseforge_optional_dependencies")!!
                                                    .split(",")
                                                    .forEach { optionalDependency(it) }
                                        }

                                        if (getGradleProperty("curseforge_tool_dependencies") !=
                                                        null
                                        ) {
                                            getGradleProperty("curseforge_tool_dependencies")!!
                                                    .split(",")
                                                    .forEach { tool(it) }
                                        }

                                        if (getGradleProperty(
                                                        "curseforge_incompatible_dependencies"
                                                ) != null
                                        ) {
                                            getGradleProperty(
                                                            "curseforge_incompatible_dependencies"
                                                    )!!
                                                    .split(",")
                                                    .forEach { incompatible(it) }
                                        }
                                    }
                            )
                }
        )

        options(closureOf<Options> { forgeGradleIntegration = false })
    }
} else {
    println("Curseforge Publishing: ☒")
}

if (isModrinthEnabled()) {
    println("Modrinth Publishing: ☑")

    configure<com.modrinth.minotaur.ModrinthExtension> {
        versionNumber.set(getGradleProperty("mod_version")!!)
        versionName.set(getGradleProperty("release_name")!!)
        versionType.set(
                if (getGradleProperty("mod_version")!!.contains("beta")) "beta" else "release"
        )
        token.set(getGradleSecret("modrinth_api_key"))
        projectId.set(getGradleProperty("modrinth_project_id")!!)
        uploadFile.set(tasks.get("remapJar"))
        gameVersions.addAll(getGradleProperty("supported_versions")!!.split(","))
        loaders.addAll(listOf("quilt"))
        changelog.set(project.rootProject.file("CHANGELOG.md").readText())

        syncBodyFrom.set(project.rootProject.file("README.md").readText())

        if (getGradleProperty("modrinth_required_dependencies") != null ||
                        getGradleProperty("modrinth_optional_dependencies") != null ||
                        getGradleProperty("modrinth_incompatible_dependencies") != null
        )
                configure<com.modrinth.minotaur.dependencies.container.DependencyDSL> {
                    if (getGradleProperty("modrinth_required_dependencies") != null) {
                        getGradleProperty("modrinth_required_dependencies")!!.split(",").forEach {
                            required.project(it)
                        }
                    }

                    if (getGradleProperty("modrinth_optional_dependencies") != null) {
                        getGradleProperty("modrinth_optional_dependencies")!!.split(",").forEach {
                            optional.project(it)
                        }
                    }

                    if (getGradleProperty("modrinth_incompatible_dependencies") != null) {
                        getGradleProperty("modrinth_incompatible_dependencies")!!.split(",")
                                .forEach { incompatible.project(it) }
                    }
                }
    }
} else {
    println("Modrinth Publishing: ☒")
}

if (isGithubEnabled()) {
    println("GitHub Publishing: ☑")

    configure<com.github.breadmoirai.githubreleaseplugin.GithubReleaseExtension> {
        token(getGradleSecret("github_token"))
        owner(getGradleProperty("github_user"))
        repo(getGradleProperty("github_repo"))
        tagName(getGradleProperty("mod_version"))
        releaseName(getGradleProperty("release_name"))
        body(project.rootProject.file("CHANGELOG.md").readText())
        prerelease(getGradleProperty("mod_version")!!.contains("beta"))
        draft(false)

        if (getGradleProperty("release_branch") != null) {
            targetCommitish(getGradleProperty("release_branch"))
        }

        val libsDir = project.file("build/libs")
        val devLibsDir = project.file("build/devLibs")

        if (libsDir.exists() && devLibsDir.exists()) {
            val archiveBaseName = getGradleProperty("archive_base_name")!!
            val libs =
                    libsDir.listFiles().filter {
                        it.name.endsWith(".jar") && it.name.contains(archiveBaseName)
                    }
            val devLibs =
                    devLibsDir.listFiles().filter {
                        it.name.endsWith(".jar") && it.name.contains(archiveBaseName)
                    }
            releaseAssets(libs, devLibs)
        }
    }
} else {
    println("GitHub Publishing: ☒")
}

tasks {
    named("publish") {
        dependsOn("remapJar")
        dependsOn("optimizeOutputsOfRemapJar")

        if (isGithubEnabled()) {
            dependsOn("githubRelease")
        }

        if (isCurseforgeEnabled()) {
            dependsOn("curseforge")
        }

        if (isModrinthEnabled()) {
            dependsOn("modrinth")
            dependsOn("modrinthSyncBody")
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
            libs.forEach { it.delete() }

            val devLibs =
                    project.file("build/devlibs").listFiles().filter { it.name.endsWith(".jar") }
            devLibs.forEach { it.delete() }
        }
    }

    if (getGradleProperty("publish_to_maven") == "true") {
        named("generateMetadataFileForMavenPublication") {
            dependsOn("optimizeOutputsOfRemapJar")
        }
    }
}

configure<PublishingExtension> {
    val enableMaven = getGradleProperty("publish_to_maven") == "true"

    publications {
        if (enableMaven) {
            println("Maven Publishing: ☑")

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
        } else {
            println("Maven Publishing: ☒")
        }
    }

    repositories {
        if (enableMaven &&
                        (getGradleSecret("maven_username") != null &&
                                getGradleSecret("maven_password") != null)
        ) {
            maven {
                name = "JamalamMavenRelease"
                url = uri("https://maven.jamalam.tech/releases")
                credentials {
                    username = getGradleSecret("maven_username")!!
                    password = getGradleSecret("maven_password")!!
                }
            }
        }
    }
}
