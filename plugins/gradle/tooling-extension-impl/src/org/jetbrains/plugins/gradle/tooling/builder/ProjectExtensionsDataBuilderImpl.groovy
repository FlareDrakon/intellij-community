// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtensionsSchema
import org.gradle.api.reflect.HasPublicType
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.model.*
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

import java.lang.reflect.Method
/**
 * @author Vladislav.Soroka
 */
@CompileStatic
class ProjectExtensionsDataBuilderImpl implements ModelBuilderService {
  private static is35_OrBetter = GradleVersion.current().baseVersion >= GradleVersion.version("3.5")

  @Override
  boolean canBuild(String modelName) {
    modelName == GradleExtensions.name
  }

  @Override
  Object buildAll(String modelName, Project project) {
    DefaultGradleExtensions result = new DefaultGradleExtensions()
    result.parentProjectPath = project.parent?.path

    result.configurations.addAll(collectConfigurations(project.configurations, false))
    result.configurations.addAll(collectConfigurations(project.buildscript.configurations, true))

    if (GradleVersion.current().baseVersion < GradleVersion.version("8.2")){
      def convention = project.convention
      convention.plugins.each { key, value ->
        result.conventions.add(new DefaultGradleConvention(key, getType(value)))
      }
    }

    def extensions = project.extensions
    extensions.extraProperties.properties.each { name, value ->
      if(name == 'extraModelBuilder' || name.contains('.')) return
      String typeFqn = getType(value)
      result.gradleProperties.add(new DefaultGradleProperty(name, typeFqn))
    }

    for (it in extensions.findAll()) {
      def extension = it as ExtensionContainer
      List<String> keyList =
        GradleVersion.current() >= GradleVersion.version("4.5")
          ? extractKeys(extension)
          : extractKeysViaReflection(extension)

      for (name in keyList) {
        def value = extension.findByName(name)
        if (value == null) continue

        def rootTypeFqn = getType(value)
        result.extensions.add(new DefaultGradleExtension(name, rootTypeFqn))
      }
    }
    return result
  }

  private static List<String> extractKeys(ExtensionContainer extension) {
    List<String> result = []
    for (final ExtensionsSchema.ExtensionSchema schema in extension.extensionsSchema) {
      result.add(schema.name)
    }
    return result
  }

  private static List<String> extractKeysViaReflection(ExtensionContainer convention) {
    List<String> methods = ["getSchema", "getAsMap"]
    Method m = null
    for (def name : methods) {
      try {
        m = convention.class.getMethod(name)
      } catch (NoSuchMethodException ignored) {
      }
      if (m != null) {
        break
      }
    }

    if (m != null) {
      return (m.invoke(convention) as Map<String, Object>).keySet().asList() as List<String>
    } else {
      return Collections.emptyList()
    }
  }

  @Override
  void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.PROJECT_EXTENSION_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project extensions data import failure")
      .withText("Unable to resolve some context data of gradle scripts. Some codeInsight features inside *.gradle files can be unavailable.")
      .withException(exception)
      .reportMessage(project)
  }

  private static @NotNull List<DefaultGradleConfiguration> collectConfigurations(
    @NotNull ConfigurationContainer configurations,
    boolean scriptClasspathConfiguration
  ) {
    def result = new ArrayList<DefaultGradleConfiguration>()
    for (configurationName in configurations.names) {
      def configuration = configurations.getByName(configurationName)
      def description = configuration.description
      def visible = configuration.visible
      def declarationAlternatives = getDeclarationAlternatives(configuration)
      result.add(new DefaultGradleConfiguration(configurationName, description, visible, scriptClasspathConfiguration, declarationAlternatives))
    }
    return result
  }

  private static @NotNull List<String> getDeclarationAlternatives(Configuration configuration) {
    try {
      Method method = configuration.class.getMethod("getDeclarationAlternatives")
      List<String> result = method.invoke(configuration) as List<String>
      return result != null ? result : Collections.<String>emptyList()
    } catch (NoSuchMethodException | SecurityException | ClassCastException ignored) {
      return Collections.emptyList()
    }
  }

  static String getType(object) {
    if (is35_OrBetter && object instanceof HasPublicType) {
      return object.publicType.toString()
    }
    def clazz = object?.getClass()?.canonicalName
    def decorIndex = clazz?.lastIndexOf('_Decorated')
    def result = !decorIndex || decorIndex == -1 ? clazz : clazz.substring(0, decorIndex)
    if (!result && object instanceof Closure) return "groovy.lang.Closure"
    return result
  }
}
