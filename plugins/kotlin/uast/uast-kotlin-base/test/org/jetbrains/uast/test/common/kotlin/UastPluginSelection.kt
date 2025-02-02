// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.test.common.kotlin

import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.FE10_SUFFIX
import org.jetbrains.uast.test.common.kotlin.UastTestSuffix.FIR_SUFFIX

interface UastPluginSelection {
    // Whether this is FIR UAST plugin or FE 1.0 UAST plugin
    val isFirUastPlugin: Boolean

    val pluginSuffix: String
        get() = if (isFirUastPlugin) FIR_SUFFIX else FE10_SUFFIX

    val counterpartSuffix: String
        get() = if (isFirUastPlugin) FE10_SUFFIX else FIR_SUFFIX
}
