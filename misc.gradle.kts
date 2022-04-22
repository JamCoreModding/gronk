tasks {
    named("build") { // Get around
        doLast {

        }
    }

    named<ProcessResources>("processResources") {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") {
            expand(
                mutableMapOf(
                    "version" to project.version
                )
            )
        }
    }

    named<org.gradle.jvm.tasks.Jar>("jar") {
        archiveBaseName.set(project.property("archive_base_name") as String)
    }

    named<org.gradle.jvm.tasks.Jar>("remapJar") {
        archiveBaseName.set(project.property("archive_base_name") as String)
    }

    withType<JavaCompile> {
        options.release.set(17)
    }
}