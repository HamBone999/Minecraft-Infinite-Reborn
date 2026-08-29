import org.gradle.api.tasks.Exec
import java.security.MessageDigest

/*
 * Minecraft Infinite -- build-time assembly.
 *
 * docs/DISTRIBUTION.md model B: this repo holds OUR source plus patches as diffs. Your
 * machine supplies your own jars, decompiles locally, applies the patches and compiles.
 * Nothing Mojang owns is committed here or served from here.
 *
 *   ./gradlew setup    once, and again after changing base/ or patches/
 *   ./gradlew build    compile and assemble
 *   ./gradlew regeneratePatches   after editing work/src, to re-diff back into patches/
 */

plugins { base }

java { toolchain { languageVersion.set(JavaLanguageVersion.of(8)) } }

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven")        // mixin
    maven("https://libraries.minecraft.net")             // launchwrapper
}

val libs = the<VersionCatalogsExtension>().named("libs")
dependencies {
    // Vendored jars do not belong in the repo -- see docs/DISTRIBUTION.md.
    compileOnly(libs.findLibrary("mixin").get())
    compileOnly(libs.findLibrary("launchwrapper").get())
    compileOnly(libs.findLibrary("asm").get())
    compileOnly(libs.findLibrary("asm-tree").get())
    compileOnly(libs.findLibrary("guava").get())
    compileOnly(libs.findLibrary("gson").get())
    compileOnly(libs.findLibrary("log4j-api").get())
}

/** Your own copy. Never downloaded by this build -- that would make it a distribution channel. */
fun localJar(prop: String, vararg fallbacks: String): File {
    (findProperty(prop) as String?)?.let { return file(it) }
    val lp = rootProject.file("local.properties")
    if (lp.exists()) {
        val p = java.util.Properties().apply { lp.inputStream().use { load(it) } }
        p.getProperty(prop)?.let { return file(it) }
    }
    fallbacks.map(::file).firstOrNull(File::exists)?.let { return it }
    throw GradleException(
        "Set $prop in local.properties or pass -P$prop=/path/to/jar.\n" +
        "This build never downloads the game -- you supply your own copy."
    )
}

fun sha1(f: File): String =
    MessageDigest.getInstance("SHA-1").digest(f.readBytes())
        .joinToString("") { "%02x".format(it) }

val verifyBase by tasks.registering {
    group = "infinite"
    description = "Check the supplied jars against base/*.sha1 before anything else runs."
    doLast {
        listOf("serverJar" to "base/server-jar.sha1", "clientJar" to "base/client-jar.sha1")
            .forEach { (prop, hashFile) ->
                val expectFile = rootProject.file(hashFile)
                if (!expectFile.exists()) return@forEach
                val expect = expectFile.readText().trim()
                if (expect.isEmpty() || expect.startsWith("#")) return@forEach
                val jar = runCatching { localJar(prop) }.getOrNull() ?: return@forEach
                val got = sha1(jar)
                if (got != expect) throw GradleException(
                    "$prop hash mismatch.\n  expected $expect\n  got      $got\n" +
                    "The patches were generated against a different jar and will misapply."
                )
                logger.lifecycle("verified $prop  $got")
            }
    }
}

/** Decompile, snapshot pristine, apply patches. Wraps the proven shell pipeline. */
val setup by tasks.registering(Exec::class) {
    group = "infinite"
    description = "Decompile the base jar and apply patches/ into work/src."
    dependsOn(verifyBase)
    workingDir = rootDir
    commandLine("sh", "scripts/setup.sh")
}

val assemblePatched by tasks.registering(Exec::class) {
    group = "infinite"
    description = "Compile the patched classes and overlay them onto a copy of the base jar."
    dependsOn(setup)
    workingDir = rootDir
    commandLine("sh", "scripts/build.sh")
}

val regeneratePatches by tasks.registering(Exec::class) {
    group = "infinite"
    description = "Re-diff work/src back into patches/ after editing it."
    workingDir = rootDir
    commandLine("sh", "scripts/mkpatch.sh")
}

val roundtripCheck by tasks.registering(Exec::class) {
    group = "verification"
    description = "Assert recompiled classes keep the original member set. See tools/deobfuscate."
    workingDir = rootDir
    commandLine("python3", "tools/deobfuscate/roundtrip-check.py",
                localJar("serverJar").absolutePath, "work/classes")
    isIgnoreExitValue = false
}

tasks.named("build") { dependsOn(assemblePatched) }
tasks.named("check") { dependsOn(roundtripCheck) }
