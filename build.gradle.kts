plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    maven { url = uri("https://mirrors.huaweicloud.com/repository/maven/") }
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(kotlin("test"))

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))

        bundledPlugins(providers.gradleProperty("platformBundledPlugins").map {
            it.split(',').map(String::trim).filter(String::isNotEmpty)
        })

        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }

        changeNotes = """                                                               
              <ul>                                                                        
                <li>修复若干问题</li>
                <li>优化性能</li>
              </ul>                                                                       
          """.trimIndent()
    }
}

tasks.test {
    useJUnitPlatform()
}

// 禁用字节码插桩以兼容非 JetBrains Runtime JDK
tasks.matching { it.name.contains("nstrumentCode") }.configureEach {
    enabled = false
}
tasks.matching { it.name.contains("instrumentTestCode", ignoreCase = true) }.configureEach {
    enabled = false
}
