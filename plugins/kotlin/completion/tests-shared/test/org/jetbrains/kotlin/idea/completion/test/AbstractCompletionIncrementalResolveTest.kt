// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils

/**
 * This test case allows to invoke the code completion twice, and to modify the
 * current file in a specific way between those two invocation.
 *
 * This allows to make sure that the code completion behaves correctly in the face
 * of the incremental resolve.
 */
abstract class AbstractCompletionIncrementalResolveTest : KotlinLightCodeInsightFixtureTestCase() {
    private val BEFORE_MARKER = "<before>" // position to invoke completion before
    private val CHANGE_MARKER = "<change>" // position to insert text specified by "TYPE" directive
    private val TYPE_DIRECTIVE_PREFIX = "// TYPE:"
    private val BACKSPACES_DIRECTIVE_PREFIX = "// BACKSPACES:"

    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected open fun doTest(testPath: String) {
        val hasCaretMarker = dataFile().readText().contains("<caret>")
        myFixture.configureByFile(fileName())

        val document = myFixture.editor.document
        val beforeMarkerOffset = document.text.indexOf(BEFORE_MARKER)
        assertTrue("\"$BEFORE_MARKER\" is missing in file \"$testPath\"", beforeMarkerOffset >= 0)

        val changeMarkerOffset = document.text.indexOf(CHANGE_MARKER)
        assertTrue("\"$CHANGE_MARKER\" is missing in file \"$testPath\"", changeMarkerOffset >= 0)

        val textToType = InTextDirectivesUtils.findArrayWithPrefixes(document.text, TYPE_DIRECTIVE_PREFIX).singleOrNull()
            ?.let { StringUtil.unquoteString(it) }
        val backspaceCount = InTextDirectivesUtils.getPrefixedInt(document.text, BACKSPACES_DIRECTIVE_PREFIX)
        assertTrue(
            "At least one of \"$TYPE_DIRECTIVE_PREFIX\" and \"$BACKSPACES_DIRECTIVE_PREFIX\" should be defined",
            textToType != null || backspaceCount != null
        )

        val beforeMarker = document.createRangeMarker(beforeMarkerOffset, beforeMarkerOffset + BEFORE_MARKER.length)
        val changeMarker = document.createRangeMarker(changeMarkerOffset, changeMarkerOffset + CHANGE_MARKER.length)
        changeMarker.isGreedyToRight = true

        project.executeWriteCommand("") {
            document.deleteString(beforeMarker.startOffset, beforeMarker.endOffset)
            document.deleteString(changeMarker.startOffset, changeMarker.endOffset)
        }

        val caretMarker = if (hasCaretMarker)
            document.createRangeMarker(editor.caretModel.offset, editor.caretModel.offset)
        else
            null
        editor.caretModel.moveToOffset(beforeMarker.startOffset)

        myFixture.complete(CompletionType.BASIC)

        project.executeWriteCommand("") {
            if (backspaceCount != null) {
                document.deleteString(changeMarker.startOffset - backspaceCount, changeMarker.startOffset)
            }
            if (textToType != null) {
                document.insertString(changeMarker.startOffset, textToType)
            }
        }

        if (caretMarker != null) {
            editor.caretModel.moveToOffset(caretMarker.startOffset)
        } else {
            editor.caretModel.moveToOffset(changeMarker.endOffset)
        }

        testCompletion(
            dataFile().readText(),
            JvmPlatforms.unspecifiedJvmPlatform,
            { completionType, count -> myFixture.complete(completionType, count) },
            additionalValidDirectives = listOf(TYPE_DIRECTIVE_PREFIX, BACKSPACES_DIRECTIVE_PREFIX)
        )
    }
}
