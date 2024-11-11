import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	java
	alias(libs.plugins.kotilin.jvm)
	alias(libs.plugins.intellij.platform)
}

repositories {
	repositories {
		maven { setUrl("https://mirrors.huaweicloud.com/repository/maven/") }
		maven { setUrl("https://mirrors.tencent.com/nexus/repository/maven-public/") }
		maven { setUrl("https://maven.aliyun.com/nexus/content/groups/public/") }
	}
	intellijPlatform {
		defaultRepositories()
	}
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
	}
	withSourcesJar()
	withJavadocJar()
}

group = "com.github.orange-guo.fill-kotlin-arguments"
version = libs.versions.fill.kotlin.arguments.get()

// https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
	pluginConfiguration {
		id = "com.github.orange-guo.fill-kotlin-arguments"
		name = "Fill Kotlin Arguments"
		version = libs.versions.fill.kotlin.arguments.get()
		vendor {
			url = "https://github.com/orange-guo"
			name = "orange-guo"
		}
		description = """
<p>A JetBrains IDEA plugin used to fill kotlin arguments.</p>
<a target="_blank" href="https://raw.githubusercontent.com/orange-guo/fill-kotlin-arguments/main/screencast.gif">
<img src="https://raw.githubusercontent.com/orange-guo/fill-kotlin-arguments/main/screencast.gif">
</a>
</p>
		""".trimIndent()
		changeNotes = ""
		ideaVersion {
			// https://confluence.jetbrains.com/display/IDEADEV/Build+Number+Ranges
			sinceBuild = "242"
			// untilBuild = "243.*"
			// The until-build attribute can be unset by setting provider { null } as a value. Note that passing only null will make Gradle use a default value instead.
			provider {
				null
			}
		}
	}
}

dependencies {
	intellijPlatform {
		intellijIdeaCommunity("2024.2")
		bundledPlugin("org.jetbrains.kotlin")
		instrumentationTools()
	}
	implementation(platform(libs.kotlin.bom))
	implementation(libs.kotlin.stdlib.jdk8)

}

tasks.withType<KotlinCompile> {
	compilerOptions {
		freeCompilerArgs = listOf("-Xcontext-receivers")
		jvmTarget = JvmTarget.fromTarget(libs.versions.java.get())
	}
}
