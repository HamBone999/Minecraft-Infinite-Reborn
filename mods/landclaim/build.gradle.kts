/*
 * An addon: a jar with META-INF/infinite.mods.toml in it.
 *
 * -proc:none is NOT optional. Mixin's annotation processor assumes an obfuscated game and
 * fails on this one -- it is the first thing that breaks a rebuild from source.
 */
plugins { java }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

sourceSets["main"].java.srcDir("src")
sourceSets["main"].resources.srcDir("resources")

repositories { mavenCentral(); maven("https://repo.spongepowered.org/maven") }

val libs = rootProject.the<VersionCatalogsExtension>().named("libs")
dependencies {
    compileOnly(libs.findLibrary("mixin").get())
    compileOnly(files(rootProject.findProperty("serverJar") ?: "", rootProject.findProperty("loaderJar") ?: ""))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-proc:none", "-nowarn"))
}

tasks.jar { archiveBaseName.set(project.name); archiveVersion.set("0.1.0") }
