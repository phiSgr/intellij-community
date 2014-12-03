/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.idea.findUsages.toClassHelper
import org.jetbrains.kotlin.psi.psiUtil.isAncestor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.psi.JetVisitorVoid
import org.jetbrains.kotlin.psi.JetClass
import org.jetbrains.kotlin.idea.findUsages.KotlinClassFindUsagesOptions
import org.jetbrains.kotlin.idea.search.usagesSearch.UsagesSearchTarget
import org.jetbrains.kotlin.idea.search.usagesSearch.UsagesSearch
import com.intellij.util.Processor
import org.jetbrains.kotlin.idea.JetBundle
import com.intellij.codeInspection.ProblemHighlightType

public class UnusedSymbolInspection : AbstractKotlinInspection() {
    override fun runForWholeFile() = true

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : JetVisitorVoid() {
            override fun visitClass(klass: JetClass) {
                if (klass.getName() == null) return

                val usagesSearchHelper = KotlinClassFindUsagesOptions(holder.getProject()).toClassHelper()
                val request = usagesSearchHelper.newRequest(UsagesSearchTarget(klass, klass.getUseScope()))
                val query = UsagesSearch.search(request)

                var foundNonTrivialUsage = false
                query.forEach(Processor {
                    usage ->
                    if (klass.isAncestor(usage.getElement())) {
                        true
                    }
                    else {
                        foundNonTrivialUsage = true
                        false
                    }
                })

                if (!foundNonTrivialUsage) {
                    holder.registerProblem(
                            klass.getNameIdentifier(),
                            JetBundle.message("unused.class", klass.getName()),
                            ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    ) // TODO add quick fix to delete it
                }
            }
        }
    }
}