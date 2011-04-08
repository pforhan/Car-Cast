package com.jadn.cc.ui;

import android.view.View;
import android.view.View.OnClickListener;

/** Ajusts current podcast time forward or back. */
public class Bumper implements OnClickListener {
    int bump;
    CarCast carCast;

    public Bumper(CarCast carCast, int bump) {
        this.bump = bump;
        this.carCast = carCast;
    }

    @Override public void onClick(View v) {
        carCast.getContentService().bump(bump);
        carCast.updateUI();
    }
}
