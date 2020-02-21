// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class JavaHomeFinderSimple extends JavaHomeFinderBase {
  private final String[] myPaths;

  protected JavaHomeFinderSimple(boolean forceEmbeddedJava, String... paths) {
    File javaHome = null;
    if (forceEmbeddedJava || Registry.is("java.detector.include.embedded", false)) javaHome = getJavaHome();
    myPaths = javaHome == null ? paths : ArrayUtil.prepend(javaHome.getAbsolutePath(), paths);
  }

  @NotNull
  public List<String> findExistingJdks() {
    ArrayList<String> result = new ArrayList<>();
    for (String path : myPaths) {
      scanFolder(new File(path), true, result);
    }
    for (File dir : guessByPathVariable()) {
      scanFolder(dir, false, result);
    }
    removeDuplicates(result, SystemInfo.isFileSystemCaseSensitive);
    return result;
  }

  public Collection<File> guessByPathVariable() {
    String pathVarString = System.getenv("PATH");
    if (pathVarString == null || pathVarString.isEmpty()) return Collections.emptyList();
    boolean isWindows = SystemInfo.isWindows;
    String suffix = isWindows ? ".exe" : "";
    ArrayList<File> dirsToCheck = new ArrayList<>(1);
    String[] pathEntries = pathVarString.split(File.pathSeparator);
    for (String p : pathEntries) {
      File dir = new File(p);
      if (StringUtilRt.equal(dir.getName(), "bin", SystemInfo.isFileSystemCaseSensitive)) {
        File f1 = new File(p, "java" + suffix);
        File f2 = new File(p, "javac" + suffix);
        if (f1.isFile() && f2.isFile()) {
          File f1c = canonize(f1);
          File f2c = canonize(f2);
          File d1 = granny(f1c);
          File d2 = granny(f2c);
          if (d1 != null && d2 != null && FileUtil.filesEqual(d1, d2)) {
            dirsToCheck.add(d1);
          }
        }
      }
    }
    return dirsToCheck;
  }

  @NotNull
  private static File canonize(@NotNull File file) {
    try {
      return file.getCanonicalFile();
    }
    catch (IOException ioe) {
      return file.getAbsoluteFile();
    }
  }

  @Nullable
  private static File granny(@Nullable File file) {
    File parent = file.getParentFile();
    return parent != null ? parent.getParentFile() : null;
  }

  private static void removeDuplicates(@NotNull ArrayList<String> strings, boolean caseSensitive) {
    int k = strings.size() - 1;
    while (k > 0) {
      String s = strings.get(k);
      for (int i = 0; i < k; i++) {
        if (StringUtil.equal(strings.get(i), s, caseSensitive)) {
          strings.remove(k);
          break;
        }
      }
      k--;
    }
  }
}
