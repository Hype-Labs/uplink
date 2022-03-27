package com.uplink.ulx.utils;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Represents an operation that accepts two input arguments and returns no
 * result.  This is the two-arity specialization of {@link java.util.function.Consumer}.
 * Unlike most other functional interfaces, {@code BiConsumer} is expected
 * to operate via side-effects.
 *
 * <p>This is a functional interface
 * whose functional method is {@link #accept(Object, Object)}.
 *
 * @param <T> the type of the first argument to the operation
 * @param <U> the type of the second argument to the operation
 *
 * @see Consumer
 */
@FunctionalInterface
public interface BiConsumer<T, U> {

   /**
    * Performs this operation on the given arguments.
    *
    * @param t the first input argument
    * @param u the second input argument
    */
   void accept(T t, U u);

   /**
    * Returns a composed {@code BiConsumer} that performs, in sequence, this
    * operation followed by the {@code after} operation. If performing either
    * operation throws an exception, it is relayed to the caller of the
    * composed operation.  If performing this operation throws an exception,
    * the {@code after} operation will not be performed.
    *
    * @param after the operation to perform after this operation
    * @return a composed {@code BiConsumer} that performs in sequence this
    * operation followed by the {@code after} operation
    * @throws NullPointerException if {@code after} is null
    */
   default BiConsumer<T, U> andThen(BiConsumer<? super T, ? super U> after) {
      Objects.requireNonNull(after);

      return (l, r) -> {
         accept(l, r);
         after.accept(l, r);
      };
   }
}
