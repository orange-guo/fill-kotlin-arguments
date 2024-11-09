import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	java
	kotlin("jvm") version "2.0.0"
	id("org.jetbrains.intellij.platform") version "2.1.0"
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

dependencies {
	intellijPlatform {
		intellijIdeaCommunity("2024.1.1")
		bundledPlugin("org.jetbrains.kotlin")
		instrumentationTools()
	}
	implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.0"))
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks.withType<KotlinCompile> {
	compilerOptions {
		freeCompilerArgs.set(listOf("-Xcontext-receivers"))
		jvmTarget.set(JvmTarget.JVM_21)
	}
}
