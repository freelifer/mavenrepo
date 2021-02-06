# mavenrepo

### 在命令assembleRelease之后生成pom.xml和上传maven脚本命令


## 接入文档

#### 1. 在项目下的build.gradle添加代码

```
classpath 'freelifer.gradle.plugin:mavenrepo-gradle-plugin:1.0.9'
```

#### 2. 在module下的build.gradle添加代码

```
// 插件名称
apply plugin: 'mavenrepo'
// 插件参数
mavenrepo {
    groupId "com.xxx.xxx"
    artifactId "xxx"
    version "xxx"
    cmd "/usr/local/maven/bin/mvn"
    repositoryId "xxxxxxxxxxxx"
    url "http://xxxxxxxxxxxxxx"
    ignore = ['com.android.support']
}
```

参数说明

| 参数 | 说明 | 默认 |
| ------ | ------- | ------- |
| repositoryId | maven仓库id | 必填 |
| url | maven仓库地址 | 必填 |
| groupId | 项目组织 | 必填 |
| artifactId | 项目名称 | module的名称 |
| version | 项目版本 | android.defaultConfig.versionName |
| cmd | maven命令 | mvn |
| ignore | 根据aar依赖生成pom.xml的忽略依赖库列表 | 空 |



## 版本发布记录

| 插件版本 | 修订日期 | 修订说明 |
| ------------ | ------------ | ------------ |
| 1.0.0 | 2019/06/20 | 初版，基础功能完成，pom.xml和shell脚本命令完成 |
| 1.0.1 | 2019/06/20 | 支持ignore忽略依赖库，支持compileOnly和provided不打入pom文件中 |
| 1.0.3 | 2020/02/15 | 支持打snapshot版本 |
| 1.0.7 | 2020/08/15 | 支持dist字段 |
| 1.0.9 | 2021/02/06 | aar忽略打入pom |