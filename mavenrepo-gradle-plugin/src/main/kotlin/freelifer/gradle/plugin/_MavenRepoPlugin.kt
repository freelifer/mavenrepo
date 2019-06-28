package freelifer.gradle.plugin

import org.codehaus.groovy.runtime.ResourceGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author zhukun on 2019-06-28.
 */
class _MavenRepoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
//        val extension = project.extensions.create<MavenRepo>("xxx")
        val extension = project.extensions.create<MavenRepo>("mavenrepo", MavenRepo::class.java)

        val mavenRepoTask = project.task("MavenRepo").doLast {
            val startTime = System.currentTimeMillis()

            if (!verifyParameter(project, extension)) {
                println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                return@doLast
            }

            val distPath = project.rootDir.path + File.separator + "dist"
            clearDistPath(distPath)

            val buildPath = project.buildDir.path
            val aarDir = File(buildPath + File.separator + "outputs" + File.separator + "aar")
            val fileArray = ArrayList<String>()
            if (aarDir.exists()) {
                val paths = aarDir.listFiles().toList()
                paths.forEach { file ->
                    fileArray.add(file.getAbsolutePath())
                }
            }
            if (fileArray.isEmpty()) {
                warn(project, "======>>maven repo not found aar file.")
                println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                return@doLast
            }
            if (fileArray.size > 1) {
                warn(project, "======>>maven repo not support multiple aar file.")
                println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                return@doLast
            }

            fileArray.forEach { path ->
                project.copy {
                    from path
                            into distPath
                            println "copy aar from ${path} to ${distPath}:"
                }
            }

            val file = File(fileArray.get(0))
            createPom(project, distPath, file.getName())

            // provided compileOnly
            println("===========>>>>>>>>>>>>>>>>>>>>ShadowPlugin ${project.dependencies.javaClass.toString()}")
//            project.extensions.create("mavenrepo", MavenRepo::class.java)

            println(">>> ${extension.ignore.toString()}")
            val dependencies = runtimeDependencies(project)

            println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
        }

        project.tasks.whenTaskAdded {
            if (it.name == "assembleRelease") {
                it.finalizedBy(mavenRepoTask)
            }
        }
    }


    private fun createPom(project: Project, distPath: String, arrName: String) {
        val dependencies = runtimeDependencies(project)

        val groupId = project.mavenrepo.groupId
        Objects.requireNonNull(groupId, "======>>maven repo not found mavenrepo.groupId")
        val artifactId = project.mavenrepo.artifactId
        Objects.requireNonNull(artifactId, "======>>maven repo not found mavenrepo.artifactId")
        val repositoryId = project.mavenrepo.repositoryId
        Objects.requireNonNull(repositoryId, "======>>maven repo not found mavenrepo.repositoryId")
        val url = project.mavenrepo.url
        Objects.requireNonNull(url, "======>>maven repo not found mavenrepo.url")

        val version = isBlank(project.mavenrepo.version) ? project.android.defaultConfig.versionName : project.mavenrepo.version
        val mvn = isBlank(project.mavenrepo.cmd) ? "mvn" : project.mavenrepo.cmd

        val pom = createPom(groupId, artifactId, version, dependencies.toString())
//        println("=====>>>>>>>>")
//        println("$pom")

        writeToFile(distPath, "pom.xml", pom)

        val shell = "${mvn} deploy:deploy-file -DgroupId=${groupId} -DartifactId=${artifactId} -Dversion=${version} -Dpackaging=aar -Dfile=${arrName} -DpomFile=pom.xml -DrepositoryId=${repositoryId} -Durl=${url}"
        writeToFile(distPath, "maven_upload.sh", shell)
    }

    private fun runtimeDependencies(project: Project): String {
        val runtimeDependencies = StringBuilder("")
        val ignoreDependencies = ignoreDependencies(project)

        val configuration: Configuration = try {
            // 3.x
            project.configurations.getByName("releaseCompileClasspath")
        } catch (e: Exception) {
            // 2.x
            project.configurations.getByName("_releaseCompile")
        }
        configuration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.forEach {
            val identifier = it.module.id
            println("${identifier.group}:${identifier.name}:${identifier.version}")
            if (!findModuleFromIgnoreDependencies(ignoreDependencies, identifier.group, identifier.name)) {
                runtimeDependencies.append(createDependency(identifier.group, identifier.name, identifier.version))
            }
        }

        return runtimeDependencies.toString()
    }


    private fun findModuleFromIgnoreDependencies(list: List<Dependency>, group: String, name: String): Boolean {
        if (list.isEmpty()) {
            return false
        }

        for (d in list) {
            if (d.name.isEmpty()) {
                if (d.group == group) {
                    return true
                }
            } else {
                if (d.group == group && d.name == name) {
                    return true
                }
            }
        }

        return false
    }

    private fun ignoreDependencies(project: Project): List<Dependency> {
        val result = ArrayList<Dependency>()
        val configurations = project.configurations
        for (configuration in configurations) {
            if (configuration == null) {
                continue
            }
            val dependencies = configuration.dependencies ?: continue
            if (dependencies.isEmpty()) {
                continue
            }
            val name = configuration.name ?: continue
            if (name.contains("provided") || name.contains("compileOnly")) {
                for (dependency in dependencies) {
                    if (dependency == null || dependency.group == null || dependency.version == null) {
                        continue
                    }
                    result.add(Dependency(dependency.group!!, dependency.name))
                    //DefaultExternalModuleDependency_Decorated
                    println("IgnoreDependency: ${dependency.group}:${dependency.name}:${dependency.version}")
                }
            }
        }

        return result
    }

    /**
     * com.android.support:appcompat-v7
     * com.android.support
     * :appcompat-v7
     */
    private fun ignoreExtensions(mavenRepo: MavenRepo): List<Dependency> {
        val result = ArrayList<Dependency>()
        if (!mavenRepo.ignore.isEmpty()) {
            val split = mavenRepo.ignore.split(",")
            if (split.isNotEmpty()) {
                for (m in split) {
                    if (m.isNotEmpty()) {
                        val moduleSplit = m.split(":")
                        if (moduleSplit.size == 1) {
                            result.add(Dependency(moduleSplit[0], ""))
                        } else if (moduleSplit.size == 2) {
                            if (moduleSplit[0].isNotEmpty()) {
                                result.add(Dependency(moduleSplit[0], moduleSplit[1]))
                            } else {
                                // Not Support group Empty!!!
                            }
                        }
                    }
                }
            }
        }
        return result
    }


    private fun verifyParameter(project: Project, mavenrepo: MavenRepo): Boolean {
        val groupId = mavenrepo.groupId
        if (groupId.length <= 0) {
            warn(project, "======>>maven repo not found mavenrepo.groupId")
            return false
        }

        val artifactId = mavenrepo.artifactId
        if (artifactId.length <= 0) {
            warn(project, "======>>maven repo not found mavenrepo.artifactId")
            return false
        }
        val repositoryId = mavenrepo.repositoryId
        if (repositoryId.length <= 0) {
            warn(project, "======>>maven repo not found mavenrepo.repositoryId")
            return false
        }
        val url = mavenrepo.url
        if (url.length <= 0) {
            warn(project, "======>>maven repo not found mavenrepo.url")
            return false
        }

        return true
    }

    //格式化输出pom文字
    private fun createPom(groupId: String, artifactId: String, version: String, dependencies: String): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://maven.apache.org/POM/4.0.0"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>$groupId</groupId>
    <artifactId>$artifactId</artifactId>
    <version>$version</version>
    <packaging>aar</packaging>
    <dependencies>
        $dependencies
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>com.simpligility.maven.plugins</groupId>
                <artifactId>android-maven-plugin</artifactId>
                <version>4.1.0</version>
                <extensions>true</extensions>
                <configuration>
                    <sign>
                        <debug>false</debug>
                    </sign>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
"""
    }

    private fun createDependency(group: String, name: String, version: String): String {
        return """
        <dependency>
            <groupId>$group</groupId>
            <artifactId>$name</artifactId>
            <version>$version</version>
        </dependency>
"""
    }


    private fun writeToFile(distPath: String, filename: String, content: String) {
        val file = File(distPath, filename)
        if (file.exists()) {
            file.delete()
        }
        val out = ResourceGroovyMethods.newPrintWriter(file)
        out.write(content)
        out.flush()
        out.close()
    }

    private fun clearDistPath(distPath: String) {
        val file = File(distPath)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun isBlank(str: String): Boolean {
        if (false || str.trim().equals("") || str.trim().equalsIgnoreCase("null")) {
            return true
        }
        return false
    }

    private fun warn(project: Project, msg: String) {
        project.getLogger().warn(msg)
    }

}

open class Dependency(var group: String, var name: String)

open class MavenRepo {
    var groupId: String = ""
    var artifactId: String = ""
    var version: String = ""
    var cmd: String = ""
    var repositoryId: String = ""
    var url: String = ""
    var ignore: String = ""
}