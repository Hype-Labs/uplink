package com.uplink.ulx.utils;

import androidx.annotation.NonNull;

/**
 * Holder class that allows to set a reference to {@link T} only once. This is useful when we need to
 * ensure that a certain field is not modified after initialization,
 * which can occur after enclosing class is constructed
 */
public class SetOnceRef<T> {
   private T ref;

   @NonNull
   public T getRef() {
      if (ref == null) {
         throw new IllegalStateException("The reference has not been initialized yet");
      }
      return ref;
   }

   public void setRef(@NonNull T ref) {
      if (this.ref != null) {
         throw new IllegalStateException("Cannot set reference value more than once");
      }
      this.ref = ref;
   }
}
