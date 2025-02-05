/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class IfStatementPostfixTemplateTest extends PostfixTemplateTestCase {

  @NotNull
  @Override
  protected String getSuffix() {
    return "if";
  }

  public void testBooleanVariableBeforeAssignment() {
    doTest();
  }

  public void testBoxedBooleanVariable() {
    doTest();
  }

  public void testNotBooleanExpression() {
    doTest();
  }

  public void testUnresolvedVariable() {
    doTest();
  }

  public void testSeveralConditions() {
    doTest();
  }

  public void testIntegerComparison() {
    doTest();
  }

  public void testMethodInvocation() {
    doTest();
  }

  public void testInstanceof() {
    doTest();
  }

  public void testInstanceofBeforeReturnStatement() {
    doTest();
  }

  public void testSimpleWithSemicolon() {
    doTest();
  }

  public void testBeforeAssignment() {
    doTest();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  public void testLesserOperatorExpression() {
    doTest();
  }
}
