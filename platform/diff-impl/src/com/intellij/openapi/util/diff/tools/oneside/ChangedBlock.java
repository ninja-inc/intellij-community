package com.intellij.openapi.util.diff.tools.oneside;

import com.intellij.openapi.util.diff.fragments.DiffFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class ChangedBlock {
  private final int myStartOffset1;
  private final int myEndOffset1;
  private final int myStartOffset2;
  private final int myEndOffset2;

  private final int myLine1;
  private final int myLine2;
  @Nullable private final List<DiffFragment> myInnerFragments;

  public ChangedBlock(int startOffset1,
                      int endOffset1,
                      int startOffset2,
                      int endOffset2,
                      int line1,
                      int line2,
                      @Nullable List<DiffFragment> innerFragments) {
    myStartOffset1 = startOffset1;
    myEndOffset1 = endOffset1;
    myStartOffset2 = startOffset2;
    myEndOffset2 = endOffset2;
    myLine1 = line1;
    myLine2 = line2;
    myInnerFragments = innerFragments;
  }

  @NotNull
  public static ChangedBlock createInserted(int length, int lines) {
    return new ChangedBlock(0, 0, 0, length, 0, lines, null);
  }

  @NotNull
  public static ChangedBlock createDeleted(int length, int lines) {
    return new ChangedBlock(0, length, 0, 0, 0, lines, null);
  }

  public int getStartOffset1() {
    return myStartOffset1;
  }

  public int getEndOffset1() {
    return myEndOffset1;
  }

  public int getStartOffset2() {
    return myStartOffset2;
  }

  public int getEndOffset2() {
    return myEndOffset2;
  }

  public int getLine1() {
    return myLine1;
  }

  public int getLine2() {
    return myLine2;
  }

  public boolean hasInsertion() {
    return myStartOffset2 != myEndOffset2;
  }

  public boolean hasDeletion() {
    return myStartOffset1 != myEndOffset1;
  }

  @Nullable
  public List<DiffFragment> getInnerFragments() {
    return myInnerFragments;
  }
}
