
plugins {
	eclipse
	java
	application
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

application {
	mainClass.set("eu._4fh.dcvotebot.Main")
}

dependencies {
	implementation("com.github.spotbugs:spotbugs-annotations:4.7.3")
	implementation("com.github.ben-manes.caffeine:caffeine:3.1.4")
	implementation("org.apache.commons:commons-lang3:3.12.0")

	implementation("com.zaxxer:HikariCP:5.0.1")
	implementation("org.mariadb.jdbc:mariadb-java-client:3.0.+")

    implementation("net.dv8tion:JDA:5.0.+") {
        exclude("club.minnced", "opus-java")
    }

	testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
	testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
	testImplementation("org.assertj:assertj-core:3.24.2")
	testImplementation("org.easymock:easymock:5.1.0")
	testImplementation("com.h2database:h2:2.1.+")
}

val test by tasks.getting(Test::class) {
	// Use junit platform for unit tests
	useJUnitPlatform()
}
