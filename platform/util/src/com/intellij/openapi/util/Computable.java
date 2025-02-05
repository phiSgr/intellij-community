// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @deprecated Use {@link Supplier} instead.
 */
@Deprecated
public interface Computable<T> extends Supplier<T> {
  T compute();

  @Override
  default T get() {
    return compute();
  }

  final class PredefinedValueComputable<T> implements Computable<T> {
    private final T myValue;

    public PredefinedValueComputable(@Nullable T value) {
      myValue = value;
    }

    @Override
    public T compute() {
      return myValue;
    }

    @Override
    public String toString() {
      return "PredefinedValueComputable{" + myValue + "}";
    }
  }

  /**
   * @deprecated Use {@link NotNullLazyValue}::getValue instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  abstract class NotNullCachedComputable<T> implements NotNullComputable<T> {
    private T myValue;

    @NotNull
    protected abstract T internalCompute();

    @NotNull
    @Override
    public final T compute() {
      T value = myValue;
      if (value == null) {
        myValue = value = internalCompute();
      }
      return value;
    }
  }
}
