
plugins {
	eclipse
	java
}

repositories {
	mavenCentral()
}

allprojects {
	group = "eu.4fh"
}

java {
	sourceCompatibility = JavaVersion.VERSION_11
	targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
	implementation("com.github.spotbugs:spotbugs-annotations:4.7.3")

	implementation("org.json:json:20220924")
	implementation("net.dv8tion:JDA:5.0.+") {
		exclude("club.minnced", "opus-java")
	}

	testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
	testImplementation("org.assertj:assertj-core:3.24.2")
	testImplementation("org.easymock:easymock:5.1.0")
}

val test by tasks.getting(Test::class) {
	// Use junit platform for unit tests
	useJUnitPlatform()
}

tasks.register<Copy>("warToTomcat") {
	dependsOn("war")
	from(layout.buildDirectory.dir("libs"))
	include("*.war")
	into(layout.buildDirectory.dir("../../../Tomcat/webapps"))
}
