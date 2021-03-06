package net.sourcebot.api.module

import com.fasterxml.jackson.databind.node.ObjectNode
import me.hwiggy.extensible.binding.jvm.classloader.JarParentClassLoader
import me.hwiggy.extensible.binding.jvm.contract.JarLoadStrategy
import me.hwiggy.extensible.exception.InvalidExtensionException
import net.sourcebot.api.configuration.JsonSerial
import java.io.File
import java.util.jar.JarFile

class ModuleLoadStrategy(
    parent: JarParentClassLoader<ModuleDescriptor, SourceModule>
) : JarLoadStrategy<ModuleDescriptor, SourceModule>(parent) {
    override fun readDescriptor(source: File) = JarFile(source).use { jar ->
        jar.getJarEntry("module.json")?.let(jar::getInputStream)?.use {
            JsonSerial.mapper.readTree(it) as ObjectNode
        }
    }?.let(::ModuleDescriptor) ?: throw InvalidExtensionException(
        "Module '${source.path}' does not contain module.json!"
    )
}