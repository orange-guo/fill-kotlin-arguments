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
		intellijIdeaCommunity("2024.2")
		bundledPlugin("com.intellij.java")
		bundledPlugin("org.jetbrains.kotlin")
		instrumentationTools()
	}
	implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.0.0"))
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

	// Lorem : An extremely useful Lorem Ipsum generator for Java!
	implementation("com.thedeanda:lorem:2.1")

	testImplementation("junit:junit:4.13.1")
	testImplementation("io.mockk:mockk:1.13.4")
	implementation("com.appmattus.fixture:fixture:1.2.0")
}

tasks.withType<KotlinCompile> {
	compilerOptions {
		freeCompilerArgs.set(listOf("-Xcontext-receivers"))
		jvmTarget.set(JvmTarget.JVM_21)
	}
}
