tasks {
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

    named("prepareRemapJar") {
	    dependsOn("optimizeOutputsOfJar")
    }
 
    named("remapJar") {
	    dependsOn("optimizeOutputsOfJar")
    }
    
    named("build") {
	    println(System.getProperty("NO_LICENSE_CHECK"))
	    if (System.getProperty("NO_LICENSE_CHECK") == "1") {
		    dependsOn.remove("checkLicenses")
		    dependsOn.remove("checkLicenseMain")
	    }
    }
}
