package com.uplink.ulx.utils;

import android.os.CountDownTimer;

public abstract class NonTickingTimer extends CountDownTimer {
   /**
    * @param millisInFuture    The number of millis in the future from the call to {@link #start()}
    *                          until the countdown is done and {@link #onFinish()} is called.
    */
   public NonTickingTimer(long millisInFuture) {
      super(millisInFuture, millisInFuture /* This should make onTick() never be called*/);
   }

   @Override
   public final void onTick(long millisUntilFinished) {
   }
}
