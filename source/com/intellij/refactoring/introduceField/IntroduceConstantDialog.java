package com.intellij.refactoring.introduceField;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemPreferencePolicy;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.TextAccessor;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.ui.*;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Iterator;

class IntroduceConstantDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.introduceField.IntroduceConstantDialog");

  private Project myProject;
  private final PsiClass myParentClass;
  private final PsiExpression myInitializerExpression;
  private final PsiLocalVariable myLocalVariable;
  private final boolean myInvokedOnDeclaration;
  private final PsiExpression[] myOccurrences;
  private final int myOccurrencesCount;
  private PsiClass myTargetClass;
  private final TypeSelectorManager myTypeSelectorManager;

  private NameSuggestionsField myNameField;
  private JCheckBox myCbReplaceAll;

  private JRadioButton myRbPrivate;
  private JRadioButton myRbProtected;
  private JRadioButton myRbpackageLocal;
  private JRadioButton myRbPublic;

  private TypeSelector myTypeSelector;
  private StateRestoringCheckBox myCbDeleteVariable;
  private final CodeStyleManager myCodeStyleManager;
  private TextAccessor myTfTargetClassName;
  private PsiClass myDestinationClass;
  private JPanel myTypePanel;
  private JPanel myTargetClassNamePanel;
  private JPanel myPanel;
  private JLabel myTypeLabel;
  private JPanel myNameSuggestionPanel;
  private JLabel myNameSuggestionLabel;
  private JLabel myTargetClassNameLabel;

  public IntroduceConstantDialog(Project project,
                                 PsiClass parentClass,
                                 PsiExpression initializerExpression,
                                 PsiLocalVariable localVariable, boolean isInvokedOnDeclaration,
                                 PsiExpression[] occurrences, PsiClass targetClass, TypeSelectorManager typeSelectorManager) {

    super(project, true);
    myProject = project;
    myParentClass = parentClass;
    myInitializerExpression = initializerExpression;
    myLocalVariable = localVariable;
    myInvokedOnDeclaration = isInvokedOnDeclaration;
    myOccurrences = occurrences;
    myOccurrencesCount = occurrences.length;
    myTargetClass = targetClass;
    myTypeSelectorManager = typeSelectorManager;
    myDestinationClass = null;

    setTitle(IntroduceConstantHandler.REFACTORING_NAME);
    myCodeStyleManager = CodeStyleManager.getInstance(myProject);
    init();

    final String ourLastVisibility = RefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY;
    if (PsiModifier.PUBLIC.equals(ourLastVisibility)) {
      myRbPublic.setSelected(true);
    } else if (PsiModifier.PROTECTED.equals(ourLastVisibility)) {
      myRbProtected.setSelected(true);
    } else if (PsiModifier.PACKAGE_LOCAL.equals(ourLastVisibility)) {
      myRbpackageLocal.setSelected(true);
    } else if (PsiModifier.PRIVATE.equals(ourLastVisibility)) {
      myRbPrivate.setSelected(true);
    } else {
      myRbPrivate.setSelected(true);
    }
  }

  public String getEnteredName() {
    return myNameField.getName();
  }

  private String getTargetClassName() {
    return myTfTargetClassName.getText();
  }

  public PsiClass getDestinationClass () {
    return myDestinationClass;
  }

  public String getFieldVisibility() {
    if (myRbPublic.isSelected()) {
      return PsiModifier.PUBLIC;
    }
    if (myRbpackageLocal.isSelected()) {
      return PsiModifier.PACKAGE_LOCAL;
    }
    if (myRbProtected.isSelected()) {
      return PsiModifier.PROTECTED;
    }
    if (myRbPrivate.isSelected()) {
      return PsiModifier.PRIVATE;
    }
    LOG.assertTrue(false);
    return null;
  }

  public boolean isReplaceAllOccurrences() {
    if (myOccurrencesCount <= 1) return false;
    return myCbReplaceAll.isSelected();
  }

  public PsiType getSelectedType() {
    return myTypeSelector.getSelectedType();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.INTRODUCE_CONSTANT);
  }

  protected JComponent createNorthPanel() {
    final NameSuggestionsManager nameSuggestionsManager;
    myTypeSelector = myTypeSelectorManager.getTypeSelector();
    myTypePanel.setLayout(new BorderLayout());
    myTypePanel.add(myTypeSelector.getComponent(), BorderLayout.CENTER);
    if (myTypeSelector.getFocusableComponent() != null) {
      myTypeLabel.setDisplayedMnemonic(KeyEvent.VK_T);
      myTypeLabel.setLabelFor(myTypeSelector.getFocusableComponent());
    }

    myNameField = new NameSuggestionsField(myProject);
    myNameSuggestionPanel.setLayout(new BorderLayout());

    myNameSuggestionPanel.add(myNameField.getComponent(), BorderLayout.CENTER);
    myNameSuggestionLabel.setLabelFor(myNameField.getFocusableComponent());

    Set<String> possibleClassNames = new LinkedHashSet<String>();
    for (int i = 0; i < myOccurrences.length; i++) {
      final PsiExpression occurrence = myOccurrences[i];
      final PsiClass parentClass = new IntroduceConstantHandler().getParentClass(occurrence);
      if (parentClass != null && parentClass.getQualifiedName() != null) {
        possibleClassNames.add(parentClass.getQualifiedName());
      }
    }
    if (possibleClassNames.size() > 1) {
      ReferenceEditorComboWithBrowseButton targetClassName =
        new ReferenceEditorComboWithBrowseButton(new ChooseClassAction(), "", PsiManager.getInstance(myProject), true);
      myTargetClassNamePanel.setLayout(new BorderLayout());
      myTargetClassNamePanel.add(targetClassName, BorderLayout.CENTER);
      myTargetClassNameLabel.setLabelFor(targetClassName);
      targetClassName.setHistory(possibleClassNames.toArray(new String[possibleClassNames.size()]));
      myTfTargetClassName = targetClassName;
      targetClassName.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          targetClassChanged();
        }
      });
    }
    else {
      ReferenceEditorWithBrowseButton targetClassName =
        new ReferenceEditorWithBrowseButton(new ChooseClassAction(), "", PsiManager.getInstance(myProject), true);
      myTargetClassNamePanel.setLayout(new BorderLayout());
      myTargetClassNamePanel.add(targetClassName, BorderLayout.CENTER);
      myTargetClassNameLabel.setLabelFor(targetClassName);
      myTfTargetClassName = targetClassName;
      targetClassName.getChildComponent().getDocument().addDocumentListener(new DocumentAdapter() {
        public void documentChanged(DocumentEvent e) {
          targetClassChanged();
        }
      });
    }

    final String propertyName;
    if (myLocalVariable != null) {
      propertyName = myCodeStyleManager.variableNameToPropertyName(myLocalVariable.getName(), VariableKind.LOCAL_VARIABLE);
    }
    else {
      propertyName = null;
    }
    nameSuggestionsManager = new NameSuggestionsManager(myTypeSelector, myNameField, new NameSuggestionsGenerator() {
      public SuggestedNameInfo getSuggestedNameInfo(PsiType type) {
        return myCodeStyleManager.suggestVariableName(
          VariableKind.STATIC_FINAL_FIELD, propertyName, myInitializerExpression, type
        );
      }

      public Pair<LookupItemPreferencePolicy, Set<LookupItem>> completeVariableName(String prefix, PsiType type) {
        LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
        LookupItemPreferencePolicy policy =
          CompletionUtil.completeVariableName(myProject, set, prefix, type, VariableKind.STATIC_FINAL_FIELD);
        return new Pair<LookupItemPreferencePolicy, Set<LookupItem>>(policy, set);
      }
    },
                                                          myProject);

    nameSuggestionsManager.setMnemonics(myTypeLabel, myNameSuggestionLabel);
    //////////
    if (myOccurrencesCount > 1) {
      ItemListener itemListener = new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          updateTypeSelector();

          myNameField.requestFocusInWindow();
        }
      };
      myCbReplaceAll.addItemListener(itemListener);
      myCbReplaceAll.setText("Replace all occurrences of expression (" + myOccurrencesCount + " occurrences)");
    }
    else {
      myCbReplaceAll.setVisible(false);
    }

    if (myLocalVariable != null) {
      if (myInvokedOnDeclaration) {
        myCbDeleteVariable.setEnabled(false);
        myCbDeleteVariable.setSelected(true);
      }
      else if (myCbReplaceAll != null) {
        updateCbDeleteVariable();
        myCbReplaceAll.addItemListener(
          new ItemListener() {
          public void itemStateChanged(ItemEvent e) {
            updateCbDeleteVariable();
          }
        });
      }
    }
    else {
      myCbDeleteVariable.setVisible(false);
    }
    updateTypeSelector();

    ButtonGroup bg = new ButtonGroup();
    bg.add(myRbPrivate);
    bg.add(myRbpackageLocal);
    bg.add(myRbProtected);
    bg.add(myRbPublic);


    updateVisibilityPanel();
    return myPanel;
  }

  private void targetClassChanged() {
    final String targetClassName = getTargetClassName();
    myTargetClass = PsiManager.getInstance(myProject).findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
    updateVisibilityPanel();
  }

  protected JComponent createCenterPanel() {
    return new JPanel();
  }

  public boolean isDeleteVariable() {
    if (myInvokedOnDeclaration) return true;
    if (myCbDeleteVariable == null) return false;
    return myCbDeleteVariable.isSelected();
  }

  private void updateCbDeleteVariable() {
    if (!myCbReplaceAll.isSelected()) {
      myCbDeleteVariable.makeUnselectable(false);
    } else {
      myCbDeleteVariable.makeSelectable();
    }
  }

  private void updateTypeSelector() {
    if (myCbReplaceAll != null) {
      myTypeSelectorManager.setAllOccurences(myCbReplaceAll.isSelected());
    } else {
      myTypeSelectorManager.setAllOccurences(false);
    }
  }

  private void updateVisibilityPanel() {
    if (myTargetClass == null) return;
    if (myTargetClass.isInterface()) {
      myRbPrivate.setEnabled(false);
      myRbProtected.setEnabled(false);
      myRbpackageLocal.setEnabled(false);
      myRbPublic.setEnabled(true);
      myRbPublic.setSelected(true);
    }
    else {
      myRbPrivate.setEnabled(true);
      myRbProtected.setEnabled(true);
      myRbpackageLocal.setEnabled(true);
      myRbPublic.setEnabled(true);
      // exclude all modifiers not visible from all occurences
      final Set<String> visible = new THashSet<String>();
      visible.add(PsiModifier.PRIVATE);
      visible.add(PsiModifier.PROTECTED);
      visible.add(PsiModifier.PACKAGE_LOCAL);
      visible.add(PsiModifier.PUBLIC);
      for (int i = 0; i < myOccurrences.length; i++) {
        PsiExpression occurrence = myOccurrences[i];
        final PsiManager psiManager = PsiManager.getInstance(myProject);
        for (Iterator<String> iterator = visible.iterator(); iterator.hasNext();) {
          String modifier = iterator.next();

          try {
            final String modifierText = modifier == PsiModifier.PACKAGE_LOCAL ? "" : modifier;
            final PsiField field = psiManager.getElementFactory().createFieldFromText(modifierText + " int xxx;", myTargetClass);
            if (!ResolveUtil.isAccessible(field, myTargetClass, field.getModifierList(), occurrence, myTargetClass)) {
              iterator.remove();
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      if (visible.contains(PsiModifier.PUBLIC)) myRbPublic.setSelected(true);
      if (visible.contains(PsiModifier.PACKAGE_LOCAL)) myRbpackageLocal.setSelected(true);
      if (visible.contains(PsiModifier.PROTECTED)) myRbProtected.setSelected(true);
      if (visible.contains(PsiModifier.PRIVATE)) myRbPrivate.setSelected(true);
    }
  }

  protected void doOKAction() {
    final String targetClassName = getTargetClassName();
    if (!"".equals (targetClassName)) {
      final PsiManager manager = PsiManager.getInstance(myProject);
      final PsiClass  newClass = manager.findClass(targetClassName, GlobalSearchScope.projectScope(myProject));
      if (newClass == null) {
        RefactoringMessageUtil.showErrorMessage(
                IntroduceConstantHandler.REFACTORING_NAME,
                "Class does not exist in the project",
                HelpID.INTRODUCE_FIELD,
                myProject);
        return;
      }
      myDestinationClass = newClass;
    }

    String fieldName = getEnteredName();
    String errorString = null;
    if ("".equals(fieldName)) {
      errorString = "No field name specified";
    } else if (!PsiManager.getInstance(myProject).getNameHelper().isIdentifier(fieldName)) {
      errorString = RefactoringMessageUtil.getIncorrectIdentifierMessage(fieldName);
    }
    if (errorString != null) {
      RefactoringMessageUtil.showErrorMessage(
              IntroduceFieldHandler.REFACTORING_NAME,
              errorString,
              HelpID.INTRODUCE_FIELD,
              myProject);
      return;
    }
    PsiField oldField = myParentClass.findFieldByName(fieldName, true);

    if (oldField != null) {
      int answer = Messages.showYesNoDialog(
              myProject,
              "The field with the name " + fieldName + "\nalready exists in class '"
              + oldField.getContainingClass().getQualifiedName() + "'.\nContinue?",
              IntroduceFieldHandler.REFACTORING_NAME,
              Messages.getWarningIcon()
      );
      if (answer != 0) {
        return;
      }
    }

    RefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = getFieldVisibility();

    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField.getComponent();
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser("Choose Destination Class", GlobalSearchScope.projectScope(myProject), new TreeClassChooser.ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      chooser.selectDirectory(myTargetClass.getContainingFile().getContainingDirectory());
      chooser.showDialog();
      PsiClass aClass = chooser.getSelectedClass();
      if (aClass != null) {
        myTfTargetClassName.setText(aClass.getQualifiedName());
      }
    }
  }
}