# mavenrepo

### auto generate pom.xml, repo maven shell

## DOC

#### project build.gradle

```
classpath 'freelifer.gradle.plugin:mavenrepo-gradle-plugin:1.0.0'
```

#### module build.gradle

```
apply plugin: 'mavenrepo'
// 配置传递给插件的参数
mavenrepo {
    groupId "com.xxx.xxx"
    artifactId "xxx"
//    version "" 默认使用android versionName
    cmd "/usr/local/maven/bin/mvn"
    repositoryId "xxxxxxxxxxxx"
    url "http://xxxxxxxxxxxxxx"
}
```
