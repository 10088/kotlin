// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.gradle.internal.impldep.com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.DefaultExternalDependencyId;
import org.jetbrains.plugins.gradle.ExternalDependencyId;

import java.util.*;

/**
 * @author Vladislav.Soroka
 */
public abstract class AbstractExternalDependency implements ExternalDependency {
  private static final long serialVersionUID = 1L;

  @NotNull
  private final DefaultExternalDependencyId myId;
  private String myScope;
  private final Collection<ExternalDependency> myDependencies;
  private String mySelectionReason;
  private int myClasspathOrder;
  private boolean myExported;

  public AbstractExternalDependency() {
    this(new DefaultExternalDependencyId(), null, null);
  }

  public AbstractExternalDependency(ExternalDependencyId id,
                                    String selectionReason,
                                    Collection<? extends ExternalDependency> dependencies) {
    myId = new DefaultExternalDependencyId(id);
    mySelectionReason = selectionReason;
    myDependencies = dependencies == null ? new ArrayList<ExternalDependency>(0) : ModelFactory.createCopy(dependencies);
  }

  public AbstractExternalDependency(ExternalDependency dependency) {
    this(
      dependency.getId(),
      dependency.getSelectionReason(),
      dependency.getDependencies()
    );
    myScope = dependency.getScope();
    myClasspathOrder = dependency.getClasspathOrder();
    myExported = dependency.getExported();
  }

  @NotNull
  @Override
  public ExternalDependencyId getId() {
    return myId;
  }

  @Override
  public String getName() {
    return myId.getName();
  }

  public void setName(String name) {
    myId.setName(name);
  }

  @Override
  public String getGroup() {
    return myId.getGroup();
  }

  public void setGroup(String group) {
    myId.setGroup(group);
  }

  @Override
  public String getVersion() {
    return myId.getVersion();
  }

  public void setVersion(String version) {
    myId.setVersion(version);
  }

  @NotNull
  @Override
  public String getPackaging() {
    return myId.getPackaging();
  }

  public void setPackaging(@NotNull String packaging) {
    myId.setPackaging(packaging);
  }

  @Nullable
  @Override
  public String getClassifier() {
    return myId.getClassifier();
  }

  public void setClassifier(@Nullable String classifier) {
    myId.setClassifier(classifier);
  }

  @Nullable
  @Override
  public String getSelectionReason() {
    return mySelectionReason;
  }

  @Override
  public int getClasspathOrder() {
    return myClasspathOrder;
  }

  public void setClasspathOrder(int order) {
    myClasspathOrder = order;
  }

  public void setSelectionReason(String selectionReason) {
    this.mySelectionReason = selectionReason;
  }

  @Override
  public String getScope() {
    return myScope;
  }

  public void setScope(String scope) {
    this.myScope = scope;
  }

  @NotNull
  @Override
  public Collection<ExternalDependency> getDependencies() {
    return myDependencies;
  }

  @Override
  public boolean getExported() {
    return myExported;
  }

  public void setExported(boolean exported) {
    myExported = exported;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AbstractExternalDependency)) return false;
    AbstractExternalDependency that = (AbstractExternalDependency)o;
    return Objects.equal(myId, that.myId) &&
           Objects.equal(myScope, that.myScope) &&
           myClasspathOrder == that.myClasspathOrder &&
           equal(myDependencies, that.myDependencies);
  }

  private static boolean equal(@NotNull Collection<ExternalDependency> dependencies1,
                               @NotNull Collection<ExternalDependency> dependencies2) {
    DependenciesIterator iterator1 = new DependenciesIterator(dependencies1);
    DependenciesIterator iterator2 = new DependenciesIterator(dependencies2);
    while (iterator2.hasNext()) {
      if (!iterator1.hasNext()) {
        return false;
      }
      AbstractExternalDependency d1 = iterator1.next();
      AbstractExternalDependency d2 = iterator2.next();
      if (!Objects.equal(d1.myId, d2.myId) || !Objects.equal(d1.myScope, d2.myScope)) {
        return false;
      }
    }
    return !iterator1.hasNext();
  }

  @Override
  public int hashCode() {
    return 31 + Objects.hashCode(myId, myScope, myClasspathOrder);
  }

  private static class DependenciesIterator implements Iterator<AbstractExternalDependency> {
    private final Set<AbstractExternalDependency> mySeenDependencies;
    private final LinkedList<ExternalDependency> myToProcess;

    private DependenciesIterator(@NotNull Collection<ExternalDependency> dependencies) {
      //noinspection unchecked
      mySeenDependencies = new THashSet<AbstractExternalDependency>(TObjectHashingStrategy.IDENTITY);
      myToProcess = new LinkedList<ExternalDependency>(dependencies);
    }

    @Override
    public boolean hasNext() {
      AbstractExternalDependency dependency = (AbstractExternalDependency)myToProcess.peekFirst();
      if (dependency == null) return false;
      if (mySeenDependencies.contains(dependency)) {
        myToProcess.removeFirst();
        return hasNext();
      }
      return !myToProcess.isEmpty();
    }

    @Override
    public AbstractExternalDependency next() {
      AbstractExternalDependency dependency = (AbstractExternalDependency)myToProcess.removeFirst();
      if (mySeenDependencies.add(dependency)) {
        myToProcess.addAll(dependency.myDependencies);
        return dependency;
      }
      else {
        return next();
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("remove");
    }
  }
}

