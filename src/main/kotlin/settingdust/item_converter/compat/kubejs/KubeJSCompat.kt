package settingdust.item_converter.compat.kubejs

import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLPaths
import settingdust.item_converter.ItemConverter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.walk

object KubeJSCompat {
    val isLoaded: Boolean by lazy {
        ModList.get().isLoaded("kubejs").also {
            if (it) ItemConverter.LOGGER.info("KubeJS detected, symlink-kubejs command available")
        }
    }

    val kubeJSDataPath: Path by lazy {
        FMLPaths.GAMEDIR.get() / "kubejs" / "data"
    }

    /**
     * Symlinks generated rules to KubeJS data folder.
     * @return Pair of (success count, error messages)
     */
    fun symlinkGeneratedToKubeJS(): Pair<Int, List<String>> {
        val errors = mutableListOf<String>()
        var linked = 0

        val exportPath = ItemConverter.exportPath
        if (!exportPath.exists()) {
            return 0 to listOf("No generated files found at $exportPath")
        }

        val namespaces = exportPath.toFile().listFiles { file -> file.isDirectory } ?: return 0 to listOf("No namespaces found")

        for (namespaceDir in namespaces) {
            val namespace = namespaceDir.name
            val sourcePath = namespaceDir.toPath()
            val targetPath = kubeJSDataPath / namespace

            try {
                targetPath.parent.createDirectories()

                if (targetPath.isSymbolicLink()) {
                    Files.delete(targetPath)
                } else if (targetPath.exists()) {
                    errors.add("$targetPath exists and is not a symlink, skipping")
                    continue
                }

                Files.createSymbolicLink(targetPath, sourcePath)
                linked++
                ItemConverter.LOGGER.info("Symlinked $sourcePath -> $targetPath")
            } catch (e: Exception) {
                errors.add("Failed to symlink $namespace: ${e.message}")
                ItemConverter.LOGGER.error("Failed to create symlink for $namespace", e)
            }
        }

        return linked to errors
    }

    /**
     * Copies generated rules to KubeJS data folder.
     * @return Pair of (success count, error messages)
     */
    fun copyGeneratedToKubeJS(): Pair<Int, List<String>> {
        val errors = mutableListOf<String>()
        var copied = 0

        val exportPath = ItemConverter.exportPath
        if (!exportPath.exists()) {
            return 0 to listOf("No generated files found at $exportPath")
        }

        val namespaces = exportPath.toFile().listFiles { file -> file.isDirectory } ?: return 0 to listOf("No namespaces found")

        for (namespaceDir in namespaces) {
            val namespace = namespaceDir.name
            val sourcePath = namespaceDir.toPath()
            val targetPath = kubeJSDataPath / namespace

            try {
                targetPath.parent.createDirectories()

                // Remove existing symlink if present
                if (targetPath.isSymbolicLink()) {
                    Files.delete(targetPath)
                }

                // Copy directory recursively
                sourcePath.walk().forEach { source ->
                    val dest = targetPath / sourcePath.relativize(source)
                    if (source.isDirectory()) {
                        dest.createDirectories()
                    } else {
                        dest.parent.createDirectories()
                        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                copied++
                ItemConverter.LOGGER.info("Copied $sourcePath -> $targetPath")
            } catch (e: Exception) {
                errors.add("Failed to copy $namespace: ${e.message}")
                ItemConverter.LOGGER.error("Failed to copy for $namespace", e)
            }
        }

        return copied to errors
    }

    /**
     * Checks if any symlinks exist in KubeJS data folder for generated namespaces.
     * @return List of namespace names that have symlinks
     */
    fun findExistingSymlinks(): List<String> {
        val exportPath = ItemConverter.exportPath
        if (!exportPath.exists()) return emptyList()

        val namespaces = exportPath.toFile().listFiles { file -> file.isDirectory } ?: return emptyList()

        return namespaces.mapNotNull { namespaceDir ->
            val targetPath = kubeJSDataPath / namespaceDir.name
            if (targetPath.isSymbolicLink()) namespaceDir.name else null
        }
    }

    /**
     * Unlinks generated rules from KubeJS data folder.
     * Only removes symlinks, not copied directories.
     * @return Pair of (success count, error messages)
     */
    fun unlinkFromKubeJS(): Pair<Int, List<String>> {
        val errors = mutableListOf<String>()
        var unlinked = 0

        val exportPath = ItemConverter.exportPath
        if (!exportPath.exists()) {
            return 0 to listOf("No generated files found")
        }

        val namespaces = exportPath.toFile().listFiles { file -> file.isDirectory } ?: return 0 to emptyList()

        for (namespaceDir in namespaces) {
            val namespace = namespaceDir.name
            val targetPath = kubeJSDataPath / namespace

            try {
                if (targetPath.isSymbolicLink()) {
                    Files.delete(targetPath)
                    unlinked++
                    ItemConverter.LOGGER.info("Unlinked $targetPath")
                }
            } catch (e: Exception) {
                errors.add("Failed to unlink $namespace: ${e.message}")
            }
        }

        return unlinked to errors
    }
}
