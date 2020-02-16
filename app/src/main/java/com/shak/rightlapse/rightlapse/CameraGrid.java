package com.shak.rightlapse.rightlapse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
public class CameraGrid extends AppCompatImageView {

    Paint linePaint;
    int width = 0;
    int height = 0;

    float wStep = 0;
    float hStep = 0;

    public CameraGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
        linePaint = new Paint();
        linePaint.setAntiAlias(true);
        linePaint.setColor(Color.WHITE);
        linePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        canvas.drawLine(wStep,0, wStep, height, linePaint);
        canvas.drawLine(wStep*2,0, wStep*2, height, linePaint);

        canvas.drawLine(0, hStep, width, hStep, linePaint);
        canvas.drawLine(0, hStep*2, width, hStep*2, linePaint);


    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        wStep = (float)(w/3);
        hStep = (float)(h/3);
        height = h;
    }
}
