/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.trivialif;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.base.MCIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public final class SplitElseIfIntention extends MCIntention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("split.else.if.intention.family.name");
  }

  @Override
  public @IntentionName @NotNull String getTextForElement(@NotNull PsiElement element) {
    return IntentionPowerPackBundle.message("split.else.if.intention.name");
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new SplitElseIfPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiJavaToken token = (PsiJavaToken)element;
    final PsiIfStatement parentStatement = (PsiIfStatement)token.getParent();
    if (parentStatement == null) {
      return;
    }
    final PsiStatement elseBranch = parentStatement.getElseBranch();
    if (elseBranch == null) {
      return;
    }
    Project project = element.getProject();
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    PsiBlockStatement blockStatement = (PsiBlockStatement)elementFactory.createStatementFromText("{}", elseBranch);
    blockStatement.getCodeBlock().add(elseBranch);
    CodeStyleManager.getInstance(project).reformat(elseBranch.replace(blockStatement));
  }
}