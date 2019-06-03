package freelifer.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration

/**
 * maven repo core plugin
 */
class _MavenRepoPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.extensions.create("mavenrepo", MavenRepo)

        Task mavenRepoTask = project.task('MavenRepo') {
            doLast {
                def startTime = System.currentTimeMillis()

                if (!verifyParameter(project)) {
                    println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                    return
                }

                def distPath = project.rootDir.path + File.separator + "dist"
                clearDistPath(distPath)

                def buildPath = project.buildDir.path
                def aarDir = new File(buildPath + File.separator + "outputs" + File.separator + "aar")
                def fileArray = []
                if (aarDir.exists()) {
                    def paths = aarDir.listFiles().toList()
                    paths.forEach { file ->
                        fileArray.add(file.getAbsolutePath())
                    }
                }
                if (fileArray.isEmpty()) {
                    warn(project,"======>>maven repo not found aar file.")
                    println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                    return
                }
                if (fileArray.size() > 1) {
                    warn(project,"======>>maven repo not support multiple aar file.")
                    println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                    return
                }

                fileArray.forEach { path ->
                    project.copy {
                        from path
                        into distPath
                        println "copy aar from ${path} to ${distPath}:"
                    }
                }

                File file = new File(fileArray.get(0))
                createPom(project, distPath, file.getName())

                println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
            }
        }

        project.tasks.whenTaskAdded { Task theTask ->
            if (theTask.name == 'assembleRelease') {
                theTask.finalizedBy(mavenRepoTask)            // 编译完apk之后再执行自定义task
            }
        }
    }

    private static boolean verifyParameter(Project project) {
        String groupId = project.mavenrepo.groupId
        if (groupId == null || groupId.length() <= 0) {
            warn(project,"======>>maven repo not found mavenrepo.groupId")
            return false
        }

        String artifactId = project.mavenrepo.artifactId
        if (artifactId == null || artifactId.length() <= 0) {
            warn(project,"======>>maven repo not found mavenrepo.artifactId")
            return false
        }
        String repositoryId = project.mavenrepo.repositoryId
        if (repositoryId == null || repositoryId.length() <= 0) {
            warn( project,"======>>maven repo not found mavenrepo.repositoryId")
            return false
        }
        String url = project.mavenrepo.url
        if (url == null || url.length() <= 0) {
            warn(project,"======>>maven repo not found mavenrepo.url")
            return false
        }

        return true
    }

    private static String createPom(String groupId, String artifactId, String version, String dependencies) {
        return """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="http://maven.apache.org/POM/4.0.0"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>${groupId}</groupId>
    <artifactId>${artifactId}</artifactId>
    <version>${version}</version>
    <packaging>aar</packaging>

    <dependencies>
        ${dependencies}
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

    private static String createDependency(String group, String name, String version) {
        return """
        <dependency>
            <groupId>${group}</groupId>
            <artifactId>${name}</artifactId>
            <version>${version}</version>
        </dependency>
"""
    }


    private static void writeToFile(String distPath, String filename, String content) {
        def file = new File(distPath, filename)
        if (file.exists()) {
            file.delete()
        }
        def out = file.newPrintWriter()
        out.write("$content")
        out.flush()
        out.close()
    }

    private static void createPom(Project project, String distPath, String arrName) {
        Configuration configuration
        try {
            // 3.x
            configuration = project.configurations."releaseCompileClasspath"
        } catch (Exception e) {
            // 2.x
            configuration = project.configurations."_releaseCompile"
        }
        def dependencies = new StringBuffer("")
        configuration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.each {
            def identifier = it.module.id
//            println("${identifier.group}:${identifier.name}:${identifier.version}")
            dependencies.append(createDependency(identifier.group, identifier.name, identifier.version))
        }

        String groupId = project.mavenrepo.groupId
        Objects.requireNonNull(groupId, "======>>maven repo not found mavenrepo.groupId")
        String artifactId = project.mavenrepo.artifactId
        Objects.requireNonNull(artifactId, "======>>maven repo not found mavenrepo.artifactId")
        String repositoryId = project.mavenrepo.repositoryId
        Objects.requireNonNull(repositoryId, "======>>maven repo not found mavenrepo.repositoryId")
        String url = project.mavenrepo.url
        Objects.requireNonNull(url, "======>>maven repo not found mavenrepo.url")

        String version = isBlank(project.mavenrepo.version) ? project.android.defaultConfig.versionName : project.mavenrepo.version
        String mvn = isBlank(project.mavenrepo.cmd) ? "mvn" : project.mavenrepo.cmd

        String pom = createPom(groupId, artifactId, version, dependencies.toString())
//        println("=====>>>>>>>>")
//        println("$pom")

        writeToFile(distPath, "pom.xml", pom)

        def shell = "${mvn} deploy:deploy-file -DgroupId=${groupId} -DartifactId=${artifactId} -Dversion=${version} -Dpackaging=aar -Dfile=${arrName} -DpomFile=pom.xml -DrepositoryId=${repositoryId} -Durl=${url}"
        writeToFile(distPath, "maven_upload.sh", shell)
    }


    private static void clearDistPath(distPath) {
        def file = new File(distPath)
        if (file.exists()) {
            file.deleteDir()
        }
    }

    private static boolean isBlank(String str){
        if(str == null || str.trim().equals("") || str.trim().equalsIgnoreCase("null")) {
            return true
        }
        return false
    }

    private static boolean warn(Project project, String msg) {
        project.getLogger().warn(msg)
    }
}