package freelifer.gradle.plugin

import com.android.build.gradle.AndroidConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

private val Project.android: AndroidConfig
    get() {
        return extensions.run {
            findByName("android") as AndroidConfig
        }
    }

private val Project.mavenrepo: MavenRepo
    get() {
        return extensions.run {
            create<MavenRepo>("mavenrepo", MavenRepo::class.java)
        }
    }

/**
 * @author zhukun on 2019-06-28.
 */
class _MavenRepoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val mavenrepo = project.mavenrepo

        val map = HashMap<String, String>()
        map[Task.TASK_GROUP] = "mavenrepo"

        val upload = project.task(map, "upload")
        upload.description = "upload maven repo"
        upload.dependsOn("assembleRelease")
        upload.doLast {
            val distFile = File(project.rootDir.path + File.separator + mavenrepo.dist)
            if (!distFile.exists()) {
                println("======>>mavenRepoTask upload error, ${distFile.absolutePath} not exists")
                return@doLast
            }
            project.exec {
                it.workingDir = distFile
                var commands = ArrayList<String>()
                if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                    commands.add("cmd")
                    commands.add("/c")
                } else {
                    commands.add("bash")
                    commands.add("-c")
                }
                commands.add("chmod +x maven_upload.sh && ./maven_upload.sh")
                it.commandLine = commands
            }
        }

        val mavenRepoTask = project.task(map, "mavenrepo").doLast {
            val startTime = System.currentTimeMillis()
            if (!verifyParameter(project, mavenrepo)) {
                println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                return@doLast
            }

            val distPath = project.rootDir.path + File.separator + mavenrepo.dist
            delDir(distPath)

            val buildPath = project.buildDir.path
            val aarDir = File(buildPath + File.separator + "outputs" + File.separator + "aar")
            var arrFilePath = ""
            var lastModifyMaxTime = 0L
            if (aarDir.exists()) {
                val paths = aarDir.listFiles().toList()
                paths.forEach { file ->
                    val lastModifyTime = file.lastModified()
                    if (lastModifyMaxTime < lastModifyTime) {
                        lastModifyMaxTime = lastModifyTime
                        arrFilePath = file.absolutePath
                    }
                }
            }
            if (arrFilePath.isEmpty()) {
                warn(project, "======>>maven repo not found aar file.")
                println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
                return@doLast
            }

            val file = File(arrFilePath)
            file.copyTo(File(distPath, file.name), true)

            val mappingDir = File(buildPath + File.separator + "outputs" + File.separator + "mapping" + File.separator + "release")
            copyMapping(mappingDir, File(distPath))

            createPom(project, mavenrepo, distPath, file.name)

            println("======>>mavenRepoTask exec time is ${System.currentTimeMillis() - startTime}ms")
        }

        project.tasks.whenTaskAdded {
            if (it.name == "assembleRelease") {
                it.finalizedBy(mavenRepoTask)
            }
        }
    }

    private fun copyMapping(src: File, dst: File) {
        if (!src.exists()) {
            return
        }
        if (!dst.exists()) {
            dst.mkdirs()
        }
        src.listFiles()?.forEach {
            it.copyTo(File(dst, it.name), true)
        }
    }

    private fun createPom(project: Project, mavenrepo: MavenRepo, distPath: String, arrName: String) {
        val dependencies = runtimeDependencies(project, mavenrepo)

        val groupId = mavenrepo.groupId
        Objects.requireNonNull(groupId, "======>>maven repo not found mavenrepo.groupId")
//        val artifactId = mavenrepo.artifactId
//        Objects.requireNonNull(artifactId, "======>>maven repo not found mavenrepo.artifactId")
        val repositoryId = mavenrepo.repositoryId
        Objects.requireNonNull(repositoryId, "======>>maven repo not found mavenrepo.repositoryId")
        val url = mavenrepo.url
        Objects.requireNonNull(url, "======>>maven repo not found mavenrepo.url")

        val artifactId = if (isBlank(mavenrepo.artifactId)) project.name else mavenrepo.artifactId
        val version = if (isBlank(mavenrepo.version)) project.android.defaultConfig.versionName else mavenrepo.version
        val mvn = if (isBlank(mavenrepo.cmd)) "mvn" else mavenrepo.cmd

        val pom = createPom(groupId, artifactId, version, dependencies)
        writeToFile(distPath, "pom.xml", pom)

        val shell = "$mvn deploy:deploy-file -DgroupId=$groupId -DartifactId=$artifactId -Dversion=$version -Dpackaging=aar -Dfile=$arrName -DpomFile=pom.xml -DrepositoryId=$repositoryId -Durl=$url"
        writeToFile(distPath, "maven_upload.sh", shell)

        val snapshotPom = createPom(groupId, artifactId, "$version-SNAPSHOT", dependencies)
        writeToFile(distPath, "pom_SNAPSHOT.xml", snapshotPom)

        val snapshotShell = "$mvn deploy:deploy-file -DgroupId=$groupId -DartifactId=$artifactId -Dversion=$version-SNAPSHOT -Dpackaging=aar -Dfile=$arrName -DpomFile=pom_SNAPSHOT.xml -DrepositoryId=$repositoryId -Durl=$url"
        writeToFile(distPath, "maven_upload-SNAPSHOT.sh", snapshotShell)
    }

    private fun runtimeDependencies(project: Project, mavenrepo: MavenRepo): String {
        val runtimeDependencies = StringBuilder("")
        val ignoreDependencies = ignoreDependencies(project)
        val ignoreExtensions = ignoreExtensions(mavenrepo)
        val ignore = ArrayList<Dependency>()
        ignore.addAll(ignoreDependencies)
        ignore.addAll(ignoreExtensions)

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
            if (!findModuleFromIgnoreDependencies(ignore, identifier.group, identifier.name)) {
                runtimeDependencies.append(createDependency(identifier.group, identifier.name, identifier.version))
            }
        }

        return runtimeDependencies.toString()
    }


    private fun findModuleFromIgnoreDependencies(list: List<Dependency>, group: String, name: String): Boolean {
        if (list.isEmpty()) {
            return false
        }

        // fix 如果group为空 忽略打包进入pom文件
        if (group.isEmpty()) {
            return true;
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
     * com.android.support:appcompat-v7 y
     * com.android.support y
     * :appcompat-v7 x
     */
    private fun ignoreExtensions(mavenRepo: MavenRepo): List<Dependency> {
        val result = ArrayList<Dependency>()

        if (mavenRepo.ignore.isNotEmpty()) {
            for (m in mavenRepo.ignore) {
                if (m.isNotEmpty()) {
                    println("ignoreExtensions: $m")
                    val moduleSplit = m.split(":")
                    if (moduleSplit.size == 1) {
                        result.add(Dependency(moduleSplit[0], ""))
//                        println("ignoreExtensions: $moduleSplit[0]:")
                    } else if (moduleSplit.size == 2) {
                        if (moduleSplit[0].isNotEmpty()) {
                            result.add(Dependency(moduleSplit[0], moduleSplit[1]))
//                            println("ignoreExtensions: $moduleSplit[0]:${moduleSplit[1]}")
                        } else {
                            // Not Support group Empty!!!
                        }
                    }
                }
            }
        }
        return result
    }


    private fun verifyParameter(project: Project, mavenrepo: MavenRepo): Boolean {
        val groupId = mavenrepo.groupId
        if (groupId.isEmpty()) {
            warn(project, "======>>maven repo not found mavenrepo.groupId")
            return false
        }
//
//        val artifactId = mavenrepo.artifactId
//        if (artifactId.isEmpty()) {
//            warn(project, "======>>maven repo not found mavenrepo.artifactId")
//            return false
//        }
        val repositoryId = mavenrepo.repositoryId
        if (repositoryId.isEmpty()) {
            warn(project, "======>>maven repo not found mavenrepo.repositoryId")
            return false
        }
        val url = mavenrepo.url
        if (url.isEmpty()) {
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

    open fun createMavenShell(): String {
        return """
mvn deploy:deploy-file -DgroupId=com.dotc.sdk -DartifactId=monsdk-cn-model -Dversion=1.13.7 -Dpackaging=aar -Dfile=monsdk-model-release-v1.13.7-rca97747.aar -DpomFile=pom.xml -DrepositoryId=${'$'}1 -Durl=${'$'}2

rc=${'$'}?
if [[ ${"$"}rc -ne '0' ]] ; then
  echo 'maven failure';
else
  echo 'maven success';
fi

json="{
    \"link\": {
        \"title\": \"智营通demo\",
        \"text\": \"android有新版本发布啦 快来测试～～ \n\n最新版本: ${"$"}{versionName}\",
        \"picUrl\": \"https://www.gstatic.cn/devrel-devsite/prod/vf4ca28c48392b1412e7b030290622a0dd55b62dec1202c59f119b1e23227c988/android/images/favicon.png\",
        \"messageUrl\": \"http://172.31.3.82:8080/apk/${"$"}{apkFileName}\"
    },
    \"at\": {
        \"atMobiles\": [
            \"15839945391\"
        ],
        \"isAtAll\": false
    },
    \"msgtype\": \"link\"
}"

# ios群 https://oapi.dingtalk.com/robot/send?access_token=cebbeb7ffe99ea9aa7b811d5ac0e1b44e44fbb4c2cb42c3d1855e442fa465b9e
# 测试群 https://oapi.dingtalk.com/robot/send?access_token=39c43f9ee9340b92713e4a9362b0d995f5520e7fb29dd579e3a17e52fb43cb90


curl 'https://oapi.dingtalk.com/robot/send?access_token=39c43f9ee9340b92713e4a9362b0d995f5520e7fb29dd579e3a17e52fb43cb90' \
   -H 'Content-Type: application/json' \
   -d "${"$"}{json}"
"""
    }

    private fun writeToFile(distPath: String, filename: String, content: String) {
        val file = File(distPath, filename)
        file.printWriter().use { out ->
            out.print(content)
        }
//        if (file.exists()) {
//            file.delete()
//        }
//        val out = ResourceGroovyMethods.newPrintWriter(file)
//        out.write(content)
//        out.flush()
//        out.close()
    }

    private fun clearDistPath(distPath: String) {
        val file = File(distPath)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun isBlank(str: String): Boolean {
        if (str.trim() == "" || str.trim().equals("null", ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun warn(project: Project, msg: String) {
        project.logger.warn(msg)
    }

    private fun delDir(dirpath: String) {
        val dir = File(dirpath)
        deleteDirWithFile(dir)
    }

    private fun deleteDirWithFile(dir: File?) {
        if (dir!!.checkFile())
            return
        for (file in dir.listFiles()) {
            if (file.isFile)
                file.delete() // 删除所有文件
            else if (file.isDirectory)
                deleteDirWithFile(file) // 递规的方式删除文件夹
        }
        dir.delete()// 删除目录本身
    }

    private fun File.checkFile(): Boolean {
        return this == null || !this.exists() || !this.isDirectory
    }
}

open class Dependency(var group: String, var name: String)

open class MavenRepo {
    var groupId: String = ""
    var artifactId: String = ""
    var version: String = ""
    var dist: String = "dist"
    var cmd: String = ""
    var repositoryId: String = ""
    var url: String = ""
    var ignore: ArrayList<String> = ArrayList()
}