// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.experimental

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.experimental.toolbar.RunWidgetAvailabilityManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

@FunctionalInterface
fun interface ExperimentalToolbarStateListener {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<ExperimentalToolbarStateListener> = Topic(
      ExperimentalToolbarStateListener::class.java,
      Topic.BroadcastDirection.NONE,
      true,
    )
  }

  /**
   * This event gets emitted when the experimental toolbar wants things dependent on its state to refresh their visibility.
   */
  fun refreshVisibility()
}

@Service
class NewToolbarRootPaneManager(private val project: Project) : SimpleModificationTracker(), Disposable {
  companion object {
    private val logger = logger<NewToolbarRootPaneManager>()
    fun getInstance(project: Project): NewToolbarRootPaneManager = project.service()
  }

  init {
    RunWidgetAvailabilityManager.getInstance(project).addListener(this, RunWidgetAvailabilityManager.RunWidgetAvailabilityListener {
      logger.info("New toolbar: run widget availability changed $it")

      IdeRootPaneNorthExtension.EP_NAME.findExtension(NewToolbarRootPaneExtension::class.java, project)?.let { extension ->
        startUpdateActionGroups(extension)
      }
    })
  }

  override fun dispose() {
  }

  internal fun startUpdateActionGroups(extension: NewToolbarRootPaneExtension) {
    incModificationCount()

    val panel = extension.panel
    if (panel.isEnabled && panel.isVisible && ToolbarSettings.getInstance().isEnabled) {
      CompletableFuture.supplyAsync(::correctedToolbarActions, AppExecutorUtil.getAppExecutorService())
        .thenAcceptAsync(Consumer {
          applyTo(it, panel, extension.layout)
        }, EdtExecutorService.getInstance())
        .exceptionally {
          thisLogger().error(it)
          null
        }
    }
  }

  @RequiresBackgroundThread
  private fun correctedToolbarActions(): Map<ActionGroup, String> {
    val actionsSchema = CustomActionsSchema.getInstance()

    return mapOf(
      "LeftToolbarSideGroup" to BorderLayout.WEST,
      IdeActions.GROUP_EXPERIMENTAL_TOOLBAR to BorderLayout.CENTER,
      (if (RunWidgetAvailabilityManager.getInstance(project).isAvailable()) "RightToolbarSideGroup" else "RightToolbarSideGroupNoRunWidget") to BorderLayout.EAST,
    ).mapKeys { (actionId, _) ->
      val action = actionsSchema.getCorrectedAction(actionId)
      action as? ActionGroup ?: throw IllegalArgumentException("Action group '$actionId' not found; actual action: $action")
    }
  }

  @RequiresEdt
  private fun applyTo(
    actions: Map<ActionGroup, String>,
    component: JComponent,
    layout: BorderLayout
  ) {
    val actionManager = ActionManager.getInstance()

    actions.mapKeys { (actionGroup, _) ->
      actionManager.createActionToolbar(
        ActionPlaces.MAIN_TOOLBAR,
        actionGroup,
        true,
      )
    }.forEach { (toolbar, layoutConstraints) ->
      toolbar.targetComponent = null
      toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY

      // We need to replace old component having the same constraints with the new one.
      layout.getLayoutComponent(component, layoutConstraints)?.let {
        component.remove(it)
      }

      component.add(toolbar.component, layoutConstraints)
    }

    component.revalidate()
    component.repaint()
  }
}

internal class NewToolbarRootPaneExtension(private val project: Project) : IdeRootPaneNorthExtension() {
  companion object {
    private const val NEW_TOOLBAR_KEY = "NEW_TOOLBAR_KEY"

    private val logger = logger<NewToolbarRootPaneExtension>()

    //internal fun getInstance(project: Project): NewToolbarRootPaneExtension? {
    //  return EP_NAME.getExtensionsIfPointIsRegistered(project)
    //    .asSequence()
    //    .filterIsInstance<NewToolbarRootPaneExtension>()
    //    .firstOrNull()
    //}
  }

  internal val layout = NewToolbarBorderLayout()
  internal val panel: JPanel = object : JPanel(layout) {
    init {
      isOpaque = true
      border = BorderFactory.createEmptyBorder(0, JBUI.scale(4), 0, JBUI.scale(4))
    }

    override fun getComponentGraphics(graphics: Graphics?): Graphics {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics))
    }
  }

  override fun getKey() = NEW_TOOLBAR_KEY

  override fun getComponent(): JPanel {
    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
    return panel
  }

  override fun uiSettingsChanged(settings: UISettings) {
    logger.info("Show old main toolbar: ${settings.showMainToolbar}; show old navigation bar: ${settings.showNavigationBar}")
    logger.info("Show new main toolbar: ${ToolbarSettings.getInstance().isVisible}")

    val toolbarSettings = ToolbarSettings.getInstance()
    panel.isEnabled = toolbarSettings.isEnabled
    panel.isVisible = toolbarSettings.isVisible && !settings.presentationMode
    project.messageBus.syncPublisher(ExperimentalToolbarStateListener.TOPIC).refreshVisibility()

    panel.revalidate()
    panel.repaint()

    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
  }

  override fun copy() = NewToolbarRootPaneExtension(project)

  override fun revalidate() {
    if (project.isDisposed) {
      logger.warn("New toolbar: Project '$project' disposal has already been initiated.")
      return
    }

    NewToolbarRootPaneManager.getInstance(project).startUpdateActionGroups(this)
  }
}