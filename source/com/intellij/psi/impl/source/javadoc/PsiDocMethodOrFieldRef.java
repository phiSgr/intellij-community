package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author mike
 */
public class PsiDocMethodOrFieldRef extends CompositePsiElement implements PsiDocTagValue, Constants {
  public PsiDocMethodOrFieldRef() {
    super(DOC_METHOD_OR_FIELD_REF);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiReference getReference() {
    final PsiElement scope = getScope();
    final PsiElement element = getNameElement();
    if (scope == null || element == null) return new MyReference(null);
    final String name = element.getText();


    final String[] signature = getSignature();

    final PsiMethod[] methods = getAllMethods(scope, this);

    nextMethod:
    for (PsiMethod method : methods) {
      if (!method.getName().equals(name)) continue;

      if (signature == null) {
        return new MyReference(method);
      }
      else {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length != signature.length) continue;
        for (int j = 0; j < parameters.length; j++) {
          PsiParameter parameter = parameters[j];
          PsiType type1 = TypeConversionUtil.erasure(parameter.getType());
          String type2 = signature[j];
          if (!Comparing.strEqual(type1.getPresentableText(), type2) && !Comparing.strEqual(type1.getCanonicalText(), type2)) continue nextMethod;
        }

        return new MyReference(method) {
          public Object[] getVariants() {
            final List<PsiMethod> lst = new ArrayList<PsiMethod>();
            for (PsiMethod method : methods) {
              if (name.equals(method.getName())) {
                lst.add(method);
              }
            }
            return lst.toArray();
          }
        };
      }
    }

    if (signature != null) return new MyReference(null);

    final PsiVariable[] vars = getAllVariables(scope, this);
    for (PsiVariable var : vars) {
      if (!var.getName().equals(name)) continue;
      return new MyReference(var);
    }

    return new MyReference(null);
  }

  public static PsiVariable[] getAllVariables(PsiElement scope, PsiElement place) {
    final SmartList<PsiVariable> result = new SmartList<PsiVariable>();
    scope.processDeclarations(new FilterScopeProcessor<PsiVariable>(new ClassFilter(PsiVariable.class), result), ResolveState.initial(), null, place);
    return result.toArray(new PsiVariable[result.size()]);
  }

  public static PsiMethod[] getAllMethods(PsiElement scope, PsiElement place) {
    final SmartList<PsiMethod> result = new SmartList<PsiMethod>();
    scope.processDeclarations(new FilterScopeProcessor<PsiMethod>(new ClassFilter(PsiMethod.class), result), ResolveState.initial(), null, place);
    return result.toArray(new PsiMethod[result.size()]);
  }

  public int getTextOffset() {
    final PsiElement element = getNameElement();

    if (element != null) {
      return element.getTextRange().getStartOffset();
    }

    return getTextRange().getEndOffset();
  }

  public PsiElement getNameElement() {
    final ASTNode sharp = findChildByType(DOC_TAG_VALUE_SHARP_TOKEN);
    if (sharp == null) return null;
    return SourceTreeToPsiMap.treeElementToPsi(sharp).getNextSibling();
  }


  private String[] getSignature() {
    PsiElement element = getNameElement().getNextSibling();

    while (element != null && !(element instanceof PsiDocTagValue)) {
      element = element.getNextSibling();
    }

    if (element == null) return null;

    List<String> types = new ArrayList<String>();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child.getNode().getElementType() == JavaDocElementType.DOC_TYPE_HOLDER) {
        final String[] typeStrings = child.getText().split("[, ]");  //avoid param types list parsing hmm mathod(paramType1, paramType2, ...) -> typeElement1, identifier2, ...
        if (typeStrings != null) {
          for (String type : typeStrings) {
            if (type.length() > 0) {
              types.add(type);
            }
          }
        }
      }
    }

    return types.toArray(new String[types.size()]);
  }

  private PsiElement getScope(){
    ChameleonTransforming.transformChildren(this);
    if (getFirstChildNode().getElementType() == ElementType.DOC_REFERENCE_HOLDER) {
      final PsiElement firstChildPsi = SourceTreeToPsiMap.treeElementToPsi(getFirstChildNode().getFirstChildNode());
      if (firstChildPsi instanceof PsiJavaCodeReferenceElement) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)firstChildPsi;
        if(referenceElement == null) return null;
        final PsiElement referencedElement = referenceElement.resolve();
        if (referencedElement instanceof PsiClass) return referencedElement;
        return null;
      }
      else if (firstChildPsi instanceof PsiKeyword) {
        final PsiKeyword keyword = (PsiKeyword)firstChildPsi;

        if (keyword.getTokenType().equals(JavaTokenType.THIS_KEYWORD)) {
          return JavaResolveUtil.getContextClass(this);
        } else if (keyword.getTokenType().equals(JavaTokenType.SUPER_KEYWORD)) {
          final PsiClass contextClass = JavaResolveUtil.getContextClass(this);
          if (contextClass != null) return contextClass.getSuperClass();
          return null;
        }
      }
    }
    return JavaResolveUtil.getContextClass(this);
  }

  private class MyReference implements PsiReference {
    private final PsiElement myReferencee;

    public MyReference(PsiElement referencee) {
      myReferencee = referencee;
    }

    public PsiElement resolve() {
      return myReferencee;
    }

    public Object[] getVariants(){
      final List<PsiModifierListOwner> vars = new ArrayList<PsiModifierListOwner>();
      final PsiElement scope = getScope();
      if (scope != null) {
        vars.addAll(Arrays.asList(getAllMethods(scope, PsiDocMethodOrFieldRef.this)));
        vars.addAll(Arrays.asList(getAllVariables(scope, PsiDocMethodOrFieldRef.this)));
      }
      return vars.toArray();
    }

    public boolean isSoft(){
      return false;
    }

    public String getCanonicalText() {
      return getNameElement().getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      final PsiElement element = getNameElement();
      final ASTNode treeElement = SourceTreeToPsiMap.psiElementToTree(element);
      final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(treeElement);
      LeafElement newToken = Factory.createSingleLeafElement(DOC_TAG_VALUE_TOKEN, newElementName, 0, newElementName.length(), charTableByTree, getManager());
      ((CompositeElement)treeElement.getTreeParent()).replaceChildInternal(SourceTreeToPsiMap.psiElementToTree(element), newToken);
      return SourceTreeToPsiMap.treeElementToPsi(newToken);
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
      if (isReferenceTo(element)) return PsiDocMethodOrFieldRef.this;
      final String name = getNameElement().getText();
      final String newName;

      final PsiMethod method;
      final PsiField field;
      final boolean hasSignature;
      final PsiClass containingClass;
      if (element instanceof PsiMethod) {
        method = (PsiMethod)element;
        hasSignature = getSignature() != null;
        containingClass = method.getContainingClass();
        newName = method.getName();
      } else if (element instanceof PsiField) {
        field = (PsiField) element;
        hasSignature = false;
        containingClass = field.getContainingClass();
        method = null;
        newName = field.getName();
      } else {
        throw new IncorrectOperationException();
      }


      ChameleonTransforming.transformChildren(getNode());
      if (getFirstChild().getNode().getElementType() == ElementType.DOC_REFERENCE_HOLDER) {
        PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement) getFirstChild().getFirstChild();
        referenceElement.bindToElement(containingClass);
      }
      else {
        if (!PsiTreeUtil.isAncestor(containingClass, PsiDocMethodOrFieldRef.this, true)) {
          final PsiReferenceExpression ref =
            JavaPsiFacade.getInstance(containingClass.getProject()).getElementFactory().createReferenceExpression(containingClass);
          addAfter(ref, null);
        }
      }

      if (hasSignature || !name.equals(newName)) {
        String text = getText();

        @NonNls StringBuffer newText = new StringBuffer();
        newText.append("/** @see ");
        if (name.equals(newName)) { // hasSignature is true here, so we can search for '('
          newText.append(text.substring(0, text.indexOf('(')));
        }
        else {
          final int sharpIndex = text.indexOf('#');
          if (sharpIndex >= 0) {
            newText.append(text.substring(0, sharpIndex + 1));
          }
          newText.append(newName);
        }
        if (hasSignature) {
          newText.append('(');
          PsiParameter[] parameters = method.getParameterList().getParameters();
          for (int i = 0; i < parameters.length; i++) {
            PsiParameter parameter = parameters[i];
            if (i > 0) newText.append(",");
            newText.append(parameter.getType().getCanonicalText());
          }
          newText.append(')');
        }
        newText.append("*/");

        PsiComment comment =
          JavaPsiFacade.getInstance(containingClass.getProject()).getElementFactory().createCommentFromText(newText.toString(), null);
        PsiElement tag = PsiTreeUtil.getChildOfType(comment, PsiDocTag.class);
        PsiElement ref = PsiTreeUtil.getChildOfType(tag, PsiDocMethodOrFieldRef.class);
        return replace(ref);
      }

      return PsiDocMethodOrFieldRef.this;
    }

    public boolean isReferenceTo(PsiElement element) {
      return element.getManager().areElementsEquivalent(element, resolve());
    }

    public TextRange getRangeInElement() {
      final ASTNode sharp = findChildByType(DOC_TAG_VALUE_SHARP_TOKEN);
      if (sharp == null) return new TextRange(0, getTextLength());
      final PsiElement nextSibling = SourceTreeToPsiMap.treeElementToPsi(sharp).getNextSibling();
      if(nextSibling != null){
        final int startOffset = nextSibling.getTextRange().getStartOffset() - getTextRange().getStartOffset();
        int endOffset = nextSibling.getTextRange().getEndOffset() - getTextRange().getStartOffset();
        final PsiElement nextParSibling = nextSibling.getNextSibling();
        if(nextParSibling != null && "(".equals(nextParSibling.getText())){
          endOffset ++;
          PsiElement nextElement = nextParSibling.getNextSibling();
          if(nextElement != null && SourceTreeToPsiMap.psiElementToTree(nextElement).getElementType() == DOC_TAG_VALUE_TOKEN){
            endOffset += nextElement.getTextLength();
            nextElement = nextElement.getNextSibling();
          }
          if(nextElement != null && ")".equals(nextElement.getText())){
            endOffset ++;
          }
        }
        return new TextRange(startOffset, endOffset);
      }
      return new TextRange(getTextLength(), getTextLength());
    }

    public PsiElement getElement() {
      return PsiDocMethodOrFieldRef.this;
    }
  }
}
