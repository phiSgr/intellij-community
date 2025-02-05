// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Companion.logLanguageFinished
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle

class NewProjectWizardLanguageStep(parent: NewProjectWizardStep) :
  AbstractNewProjectWizardMultiStepWithAddButton<NewProjectWizardLanguageStep, LanguageNewProjectWizard>(parent, LanguageNewProjectWizard.EP_NAME),
  LanguageNewProjectWizardData,
  NewProjectWizardBaseData by parent.baseData {

  override val self = this

  override val label = UIBundle.message("label.project.wizard.new.project.language")

  override val languageProperty by ::stepProperty
  override var language by ::step

  override var additionalStepPlugins = mapOf(
    "Go" to "com.intellij.go",
    "Ruby" to "com.intellij.ruby",
    "PHP" to "com.intellij.php",
    "Python" to "com.intellij.python",
    "Scala" to "com.intellij.scala",
  )

  init {
    data.putUserData(LanguageNewProjectWizardData.KEY, this)
    languageProperty.afterChange {
      NewProjectWizardCollector.logLanguageChanged(context, this::class.java)
    }
  }

  override fun setupProject(project: Project) {
    super.setupProject(project)
    logLanguageFinished(context, this::class.java)
  }
}
