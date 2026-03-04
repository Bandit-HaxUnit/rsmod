plugins {
    id("base-conventions")
    application
}

application {
    mainClass.set("org.rsmod.server.app.GameServerKt")
    applicationDefaultJvmArgs = listOf("-XX:AutoBoxCacheMax=65535", "-Xms1g")
}

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.clikt)
    implementation(libs.guice)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.openrs2.cache)
    implementation(projects.api.cache)
    implementation(projects.api.core)
    implementation(projects.api.gameProcess)
    implementation(projects.api.invPlugin)
    implementation(projects.api.net)
    implementation(projects.api.objPlugin)
    implementation(projects.api.parsers.jackson)
    implementation(projects.api.parsers.json)
    implementation(projects.api.parsers.toml)
    implementation(projects.api.registry)
    implementation(projects.api.shops)
    implementation(projects.engine.annotations)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.module)
    implementation(projects.engine.routefinder)
    implementation(projects.engine.plugin)
    implementation(projects.server.install)
    implementation(projects.server.logging)
    implementation(projects.server.services)
    implementation(projects.server.shared)
}

tasks.named<JavaExec>("run") {
    description = "Runs the RS Mod game server"
    workingDir = rootProject.projectDir
    // During revision migration, cached identity hashes in source refs can be stale.
    // Default to skipping hash verification for `run`; set `-PverifyTypeHashes=true`
    // to re-enable strict verification.
    val verifyTypeHashes =
        providers.gradleProperty("verifyTypeHashes").orNull?.toBooleanStrictOrNull() ?: false
    if (!verifyTypeHashes) {
        args("--skip-type-verification")
    }
}
