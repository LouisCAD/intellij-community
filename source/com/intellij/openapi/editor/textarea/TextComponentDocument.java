package com.intellij.openapi.editor.textarea;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.beans.PropertyChangeListener;

/**
 * @author yole
 */
public class TextComponentDocument extends UserDataHolderBase implements Document {
  private JTextComponent myTextComponent;

  public TextComponentDocument(final JTextComponent textComponent) {
    myTextComponent = textComponent;
  }

  public String getText() {
    try {
      final javax.swing.text.Document document = myTextComponent.getDocument();
      return document.getText(0, document.getLength()-1);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public CharSequence getCharsSequence() {
    return getText();
  }

  public char[] getChars() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getTextLength() {
    return myTextComponent.getDocument().getLength();
  }

  public int getLineCount() {
    return 1;
  }

  public int getLineNumber(final int offset) {
    return 0;
  }

  public int getLineStartOffset(final int line) {
    return 0;
  }

  public int getLineEndOffset(final int line) {
    return getTextLength();
  }

  public void insertString(final int offset, final CharSequence s) {
    try {
      myTextComponent.getDocument().insertString(offset, s.toString(), null);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public void deleteString(final int startOffset, final int endOffset) {
    try {
      myTextComponent.getDocument().remove(startOffset, endOffset - startOffset);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public void replaceString(final int startOffset, final int endOffset, final CharSequence s) {
    final javax.swing.text.Document document = myTextComponent.getDocument();
    try {
      document.remove(startOffset, endOffset-startOffset);
      document.insertString(startOffset, s.toString(), null);
    }
    catch (BadLocationException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isWritable() {
    return true;
  }

  public long getModificationStamp() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void fireReadOnlyModificationAttempt() {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void addDocumentListener(final DocumentListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void addDocumentListener(final DocumentListener listener, final Disposable parentDisposable) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeDocumentListener(final DocumentListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public RangeMarker createRangeMarker(final int startOffset, final int endOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public RangeMarker createRangeMarker(final int startOffset, final int endOffset, final boolean surviveOnExternalChange) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public MarkupModel getMarkupModel() {
    throw new UnsupportedOperationException("Not implemented");
  }

  @NotNull
  public MarkupModel getMarkupModel(@Nullable final Project project) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void addPropertyChangeListener(final PropertyChangeListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removePropertyChangeListener(final PropertyChangeListener listener) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void setReadOnly(final boolean isReadOnly) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public RangeMarker createGuardedBlock(final int startOffset, final int endOffset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void removeGuardedBlock(final RangeMarker block) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public RangeMarker getOffsetGuard(final int offset) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Nullable
  public RangeMarker getRangeGuard(final int start, final int end) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void startGuardedBlockChecking() {
  }

  public void stopGuardedBlockChecking() {
  }

  public void setCyclicBufferSize(final int bufferSize) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public void setText(final CharSequence text) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public RangeMarker createRangeMarker(final TextRange textRange) {
    throw new UnsupportedOperationException("Not implemented");
  }

  public int getLineSeparatorLength(final int line) {
    return 0;
  }
}