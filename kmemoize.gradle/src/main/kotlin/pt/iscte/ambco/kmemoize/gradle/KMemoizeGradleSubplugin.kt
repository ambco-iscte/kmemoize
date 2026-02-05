package pt.iscte.ambco.kmemoize.gradle

import com.google.auto.service.AutoService
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.*

@AutoService(KotlinCompilerPluginSupportPlugin::class)
class KMemoizeGradleSubplugin: KotlinCompilerPluginSupportPlugin {

    companion object {
        const val VERSION = "0.4.2"
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact("io.github.ambco-iscte", "kmemoize.compiler", VERSION)

    override fun getCompilerPluginId() = "io.github.ambco-iscte.kmemoize"
}