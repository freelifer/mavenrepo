# mavenrepo-v2

### 利用gradle脚本，编写模版，添加配置，上传maven仓库脚本命令

## 接入文档

#### 1. 在module下的build.gradle添加代码

```
apply from: 'https://gitee.com/freelifer/android/raw/master/gradle-mvn-push.gradle'
```

#### 2. 在项目目录下的local.properties

```
# 仓库认证用户名
authUsername=xxxxxxx
# 仓库认证密码
authPassword=xxxxxxx
```

#### 3. 在项目目录下的gradle.properties

```
# 正式仓库地址
RELEASE_REPOSITORY_URL=

# 快照仓库地址
SNAPSHOT_REPOSITORY_URL=
```

#### 4. 在module目录下的gradle.properties，配置当前sdk的信息
```
POM_NAME=MonSDK
POM_GROUP_ID=com.qb
POM_ARTIFACT_ID=monsdk
#POM_VERSION=3.0.0
POM_PACKAGING=aar
POM_DEPS_IGNORE=androidx.appcompat:aa, appcompat ,,,abc,:okhttp,:,com.android.support
```
参数说明

| 参数 | 说明 | 默认 |
| ------ | ------- | ------- |
| POM_NAME | sdk的全名称 | 必填 |
| POM_GROUP_ID | sdk上传maven的组织名称 | 必填 |
| POM_ARTIFACT_ID | sdk上传maven的名称 | 选填，默认module的名称 |
| POM_VERSION | sdk上传maven的版本 | 选填，android.defaultConfig.versionName |
| POM_PACKAGING | 包类型 | 选填，默认aar| 
| POM_DEPS_IGNORE | 根据aar依赖生成pom.xml的忽略依赖库列表 | 空 |


## 测试

执行installLocally命令，在${rootProject.buildDir}/localMaven 目录下，检查

## 上蹿

执行 gradle build
gradle task uploadArchives

## 版本发布记录

| 插件版本 | 修订日期 | 修订说明 |
| ------------ | ------------ | ------------ |
| 1.0.0 | 2019/06/20 | 初版，基础功能完成，pom.xml和shell脚本命令完成 |
| 1.0.1 | 2019/06/20 | 支持ignore忽略依赖库，支持compileOnly和provided不打入pom文件中 |
| 1.0.3 | 2020/02/15 | 支持打snapshot版本 |
| 1.0.7 | 2020/08/15 | 支持dist字段 |
| 1.0.9 | 2021/02/06 | aar忽略打入pom |
| 2.0.0 | 2021/09/15 | 去除mvn客户端依赖，直接利用gradle maven plugin上传aar |