// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import com.intellij.openapi.roots.DependencyScope
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.konan.NativePlatforms

public class GradleTestCase(val projectName: String,
                            val modules: MutableList<Module> = mutableListOf(),
                            val assertType: AssertType
)
{
    public fun addModule(module: Module)  : GradleTestCase
    {
        modules.add(module)
        return this
    }


    public fun ProjectSturcture() : String
    {
        var result = StringBuilder()
        var dependencies = StringBuilder()
        for (module in modules.filter{ it.isSource })
        {
            result.appendLine( "MODULE ${module.name} { platform=[${module.TargetsString()}] }")
            dependencies.append(module.GetDependenciesString())
        }
        result.appendLine()
        result.append(dependencies)
        return result.toString().replace("\n", "\r\n")
    }
}
class Module(val name:String,
             val targets: MutableList<TargetPlatform> = mutableListOf(),
             val dependencies: MutableList<Dependency> = mutableListOf(),
             val isSource: Boolean
)
{
    fun moduleDependency(name: String,depScope:DependencyScope,depType:DepType) : Module
    {
        dependencies.add(Dependency(name, depScope, depType))
        return this
    }
    fun moduleTargets(vararg platforms: TargetPlatform) : Module
    {
        targets.addAll(platforms)
        return this
    }

    fun TargetsString() : String
    {
        return targets.map{it -> when(it)
        {
            JvmPlatforms.defaultJvmPlatform -> "JVM"
            JsPlatforms.defaultJsPlatform -> "JS"
            NativePlatforms.unspecifiedNativePlatform -> "Native"
            else -> "unknown"
        }
        }.joinToString()
    }

    fun GetDependenciesString() : String
    {
        var dependenciesString = StringBuilder()
        for (dependency in dependencies)
        {
            dependenciesString.appendLine("$name -> ${dependency.name} { kind=${dependency.depType} }")
        }
        return dependenciesString.toString()
    }
}
class Dependency(val name: String,
                 val depScope: DependencyScope,
                 val depType: DepType )
enum class DepType { DEPENDS_ON, DEPENDENCY }
enum class AssertType {assertExhaustiveModuleDependencyList, assertNoDependencyInBuildClasses}