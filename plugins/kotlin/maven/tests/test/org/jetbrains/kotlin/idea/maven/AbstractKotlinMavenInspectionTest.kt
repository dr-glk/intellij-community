// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.codeInspection.QuickFix
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.idea.core.util.toPsiDirectory
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.maven.inspections.KotlinMavenPluginPhaseInspection
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.utils.keysToMap
import java.io.File

abstract class AbstractKotlinMavenInspectionTest : KotlinMavenImportingTestCase() {

    override fun setUp() {
        super.setUp()
        createStdProjectFolders()
    }

    fun doTest(fileName: String) {
        val pomFile = File(fileName)
        val pomText = pomFile.readText()

        createPomFile(fileName)
        importProject()
        myProject.allModules().forEach {
            setupJdkForModule(it.name)
        }

        if (pomText.contains("<!--\\s*mkjava\\s*-->".toRegex(RegexOption.MULTILINE))) {
            mkJavaFile()
        }

        val inspectionClassName = "<!--\\s*inspection:\\s*([\\S]+)\\s-->".toRegex().find(pomText)?.groups?.get(1)?.value
            ?: KotlinMavenPluginPhaseInspection::class.qualifiedName!!
        val inspectionClass = Class.forName(inspectionClassName)

        val matcher = "<!--\\s*problem:\\s*on\\s*([^,]+),\\s*title\\s*(.+)\\s*-->".toRegex()
        val expectedProblemsText = pomText.lines()
            .filter { matcher.matches(it) }
            .joinToString("\n")

        val problemElements = runInspection(inspectionClass, myProject).problemElements
        val actualProblems = problemElements
            .keys()
            .filter { it.name == "pom.xml" }
            .map { problemElements.get(it) }
            .flatMap { it.toList() }
            .mapNotNull { it as? ProblemDescriptorBase }

        val actual = actualProblems
            .map { SimplifiedProblemDescription(it.descriptionTemplate, it.psiElement.text.replace("\\s+".toRegex(), "")) to it }
            .sortedBy { it.first.text }

        val actualProblemsText = actual.map { it.first }.joinToString("\n") { "<!-- problem: on ${it.elementText}, title ${it.text} -->" }

        assertEquals(expectedProblemsText, actualProblemsText)

        val suggestedFixes = actual.flatMap { p -> p.second.fixes?.sortedBy { it.familyName }?.map { p.second to it } ?: emptyList() }

        val filenamePrefix = pomFile.nameWithoutExtension + ".fixed."
        val fixFiles =
            pomFile.parentFile.listFiles { _, name -> name.startsWith(filenamePrefix) && name.endsWith(".xml") }.sortedBy { it.name }

        val rangesToFixFiles: Map<File, IntRange> = fixFiles.keysToMap { file ->
            val fixFileName = file.name
            val fixRangeStr = fixFileName.substringBeforeLast('.').substringAfterLast('.')
            val numbers = fixRangeStr.split('-').map { it.toInt() }
            when (numbers.size) {
                0 -> error("No number in fix file $fixFileName")
                1 -> IntRange(numbers[0], numbers[0])
                2 -> IntRange(numbers[0], numbers[1])
                else -> error("Bad range `$fixRangeStr` in fix file $fixFileName")
            }
        }

        val sortedFixRanges = rangesToFixFiles.values.sortedBy { it.first }
        sortedFixRanges.forEachIndexed { i, range ->
            if (i > 0) {
                val previous = sortedFixRanges[i - 1]
                if (previous.last + 1 != range.first) {
                    error("Bad ranges in fix files: $previous and $range")
                }
            }
        }

        val numberOfFixDataFiles = sortedFixRanges.lastOrNull()?.endInclusive ?: 0
        if (numberOfFixDataFiles > suggestedFixes.size) {
            fail("Not all fixes were suggested by the inspection: expected count: ${fixFiles.size}, actual fixes count: ${suggestedFixes.size}")
        }
        if (numberOfFixDataFiles < suggestedFixes.size) {
            fail("Not all fixes covered by *.fixed.N.xml files")
        }

        val documentManager = PsiDocumentManager.getInstance(myProject)
        val document = documentManager.getDocument(PsiManager.getInstance(myProject).findFile(myProjectPom)!!)!!
        val originalText = document.text

        suggestedFixes.forEachIndexed { index, suggestedFix ->
            val (problem, quickfix) = suggestedFix
            val file = rangesToFixFiles.entries.first { (_, range) -> index + 1 in range }.key

            applyFix(quickfix, problem)

            KotlinTestUtils.assertEqualsToFile(file, document.text.trim())

            ApplicationManager.getApplication().runWriteAction {
                document.setText(originalText)
                documentManager.commitDocument(document)
            }
        }
    }

    private fun createPomFile(fileName: String) {
        myProjectPom = myProjectRoot.findChild("pom.xml")
        if (myProjectPom == null) {
            myProjectPom = runWriteAction(ThrowableComputable {
                myProjectRoot.createChildData(null, "pom.xml")
            })
        }
        myAllPoms.add(myProjectPom!!)

        ApplicationManager.getApplication().runWriteAction {
            myProjectPom!!.setBinaryContent(File(fileName).readBytes())
        }
    }

    private fun <D : ProblemDescriptor> applyFix(quickFix: QuickFix<in D>, desc: D) {
        CommandProcessor.getInstance().executeCommand(
            myProject,
            {
                ApplicationManager.getApplication().runWriteAction {
                    quickFix.applyFix(myProject, desc)

                    val manager = PsiDocumentManager.getInstance(myProject)
                    val document = manager.getDocument(PsiManager.getInstance(myProject).findFile(myProjectPom)!!)!!
                    manager.doPostponedOperationsAndUnblockDocument(document)
                    manager.commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)
                }

                println(myProjectPom.contentsToByteArray().toString(Charsets.UTF_8))
            },
            "quick-fix-$name", "Kotlin",
        )
    }

    private fun mkJavaFile() {
        val contentEntry = getContentRoots(myProject.allModules().single().name).single()
        val sourceFolder =
            contentEntry.getSourceFolders(JavaSourceRootType.SOURCE).singleOrNull() ?: contentEntry.getSourceFolders(SourceKotlinRootType)
                .singleOrNull()
        ApplicationManager.getApplication().runWriteAction {
            val javaFile = sourceFolder?.file?.toPsiDirectory(myProject)?.createFile("Test.java") ?: throw IllegalStateException()
            javaFile.viewProvider.document!!.setText("class Test {}\n")
        }

        assertTrue(FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, myProject.allModules().single().moduleScope))
    }

    private data class SimplifiedProblemDescription(val text: String, val elementText: String)
}