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
		languageVersion.set(JavaLanguageVersion.of("21"))
	}
	withSourcesJar()
	withJavadocJar()
}

group = "com.github.orange-guo.fill-kotlin-arguments"
version = "0.0.1"

intellijPlatform {
	pluginConfiguration {
		ideaVersion {
			// https://confluence.jetbrains.com/display/IDEADEV/Build+Number+Ranges
			sinceBuild = "242"
			untilBuild = "243.*"
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
		jvmTarget = JvmTarget.JVM_21
	}
}
