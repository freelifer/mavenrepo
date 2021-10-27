# mavenrepo

> 用于aar或jar上传到maven仓库

v2版本已上线，利用gradle脚本，通过简单配置实现aar或jar的maven仓库上传，[v1版本入口地址](./README-v1.md)

## 接入手册


#### 1. 在项目目录下的gradle.properties，添加仓库地址

```
# 正式仓库地址
RELEASE_REPOSITORY_URL=

# 快照仓库地址
SNAPSHOT_REPOSITORY_URL=
```

#### 2. 在项目目录下的local.properties，添加仓库认证用户信息

```
# 仓库认证用户名
authUsername=xxxxxxx
# 仓库认证密码
authPassword=xxxxxxx
```

#### 3. 在需要集成打包服务的module下的build.gradle添加插件依赖

```
// gitee地址
apply from: 'https://gitee.com/freelifer/mavenrepo/raw/master/shell/2.0.0/gradle-mvn-push.gradle'

// github地址
apply from: 'https://raw.githubusercontent.com/freelifer/mavenrepo/master/shell/2.0.0/gradle-mvn-push.gradle'
```

#### 4. 在需要集成打包服务module目录下的gradle.properties，配置当前sdk的信息
```
POM_NAME=
POM_GROUP_ID=
POM_ARTIFACT_ID=
#POM_VERSION=
#POM_PACKAGING=aar
POM_DEPS_IGNORE=androidx.appcompat,com.android.support
```
参数说明

| 参数 | 说明 | 默认 |
| ------ | ------- | ------- |
| POM_NAME | sdk的全名称 | 必填 |
| POM_GROUP_ID | sdk上传maven的组织名称 | 必填 |
| POM_ARTIFACT_ID | sdk上传maven的名称 | 选填，默认module的名称 |
| POM_VERSION | sdk上传maven的版本 | 选填，android.defaultConfig.versionName |
| POM_PACKAGING | 包类型 | 选填，默认aar| 
| POM_DEPS_IGNORE | 根据aar依赖生成pom.xml的忽略依赖库列表，用逗号隔开，支持格式：groupId、groupId:artifactId、:artifactId | 空 |


## 测试验证

执行installLocally命令，在${rootProject.buildDir}/localMaven 目录下，检查是否生成对应的aar和pom文件

## 上传

```
# mac
./gradlew :sdk:uploadArchives

# window
gradlew.bat :sdk:uploadArchives

```

## 版本发布记录

| 插件版本 | 修订日期 | 修订说明 |
| ------------ | ------------ | ------------ |
| 2.0.0 | 2021/10/27 | 新版本库上传maven发布 |