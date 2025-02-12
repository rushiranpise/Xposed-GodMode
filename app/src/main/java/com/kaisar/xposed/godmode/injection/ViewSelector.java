package com.kaisar.xposed.godmode.injection;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;
import static com.kaisar.xposed.godmode.injection.util.CommonUtils.recycleNullableBitmap;

import android.animation.Animator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.widget.TooltipCompat;

import com.kaisar.xposed.godmode.R;
import com.kaisar.xposed.godmode.injection.GodModeInjector;
import com.kaisar.xposed.godmode.injection.ViewController;
import com.kaisar.xposed.godmode.injection.ViewHelper;
import com.kaisar.xposed.godmode.injection.bridge.GodModeManager;
import com.kaisar.xposed.godmode.injection.util.GmResources;
import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.injection.util.Property;
import com.kaisar.xposed.godmode.injection.weiget.MaskView;
import com.kaisar.xposed.godmode.injection.weiget.ParticleView;
import com.kaisar.xposed.godmode.rule.ViewRule;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public final class ViewSelector implements Property.OnPropertyChangeListener<Boolean>, SeekBar.OnSeekBarChangeListener {

    private static final int OVERLAY_COLOR = Color.argb(150, 255, 0, 0);
    private final List<WeakReference<View>> mViewNodes = new ArrayList<>();
    private int mCurrentViewIndex = 0;

    private MaskView mMaskView;
    private View mNodeSelectorPanel;
    private Activity activity = null;
    private SeekBar seekbar = null;
    public static volatile boolean mKeySelecting = false;

    public ViewSelector(){
        XposedHelpers.findAndHookMethod(Activity.class, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam param) {
                if (GodModeInjector.switchProp.get()) {
                    KeyEvent event = (KeyEvent) param.args[0];
                    int action = event.getAction();
                    int keyCode = event.getKeyCode();
                    if (action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        seekbaradd();
                    }else if(action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_VOLUME_UP){
                        seekbarreduce();
                    }
                    param.setResult(true);
                }
            }
        });
    }

    public void setTopActivity(final Activity a) {
        Logger.d(TAG+".SelectPanel","set top activity: " + a);
        activity = a;
        ViewController.cleanStack();
    }

    public void setPanel(Boolean display) {
        if (activity == null) return;
        if (display) {
            showNodeSelectPanel(activity);
        } else {
            dismissNodeSelectPanel();
        }
    }


    private void showNodeSelectPanel(final Activity activity) {
        mViewNodes.clear();
        mCurrentViewIndex = 0;
        //build view hierarchy tree
        mViewNodes.addAll(ViewHelper.buildViewNodes(activity.getWindow().getDecorView()));
        final ViewGroup container = (ViewGroup) activity.getWindow().getDecorView();
        mMaskView = MaskView.makeMaskView(activity);
        mMaskView.setMaskOverlay(OVERLAY_COLOR);
        mMaskView.attachToContainer(container);
        try {
            GodModeInjector.injectModuleResources(activity.getResources());
            LayoutInflater layoutInflater = LayoutInflater.from(activity);
            mNodeSelectorPanel = layoutInflater.inflate(GodModeInjector.moduleRes.getLayout(R.layout.layout_node_selector), container, false);
            seekbar = mNodeSelectorPanel.findViewById(R.id.slider);
            seekbar.setMax(mViewNodes.size() - 1);
            seekbar.setOnSeekBarChangeListener(this);
            View btnBlock = mNodeSelectorPanel.findViewById(R.id.block);
            TooltipCompat.setTooltipText(btnBlock, GmResources.getText(R.string.accessibility_block));
            btnBlock.setOnClickListener(v -> {
                try {
//                    mNodeSelectorPanel.setAlpha(0f);
                    final View view = mViewNodes.get(mCurrentViewIndex).get();
                    Logger.d(TAG, "removed view = " + view);
                    if (view != null) {
                        //hide overlay
                        mMaskView.updateOverlayBounds(new Rect());
                        final Bitmap snapshot = ViewHelper.snapshotView(ViewHelper.findTopParentViewByChildView(view));
                        final ViewRule viewRule = ViewHelper.makeRule(view);
                        final ParticleView particleView = new ParticleView(activity);
                        particleView.setDuration(1000);
                        particleView.attachToContainer(container);
                        particleView.setOnAnimationListener(new ParticleView.OnAnimationListener() {
                            @Override
                            public void onAnimationStart(View animView, Animator animation) {
                                viewRule.visibility = View.GONE;
                                ViewController.applyRule(view, viewRule,true);
                                GodModeManager.getDefault().writeRule(activity.getPackageName(), viewRule, snapshot);
                            }

                            @Override
                            public void onAnimationEnd(View animView, Animator animation) {
                                recycleNullableBitmap(snapshot);
                                particleView.detachFromContainer();
//                                mNodeSelectorPanel.animate()
//                                        .alpha(1.0f)
//                                        .setInterpolator(new DecelerateInterpolator(1.0f))
//                                        .setDuration(300)
//                                        .start();
                            }
                        });
                        particleView.boom(view);
                    }
                    mViewNodes.remove(mCurrentViewIndex--);
                    seekbar.setMax(mViewNodes.size() - 1);
                } catch (Exception e) {
                    Logger.e(TAG, "block fail", e);
                    Toast.makeText(activity, GmResources.getString(R.string.block_fail, e.getMessage()), Toast.LENGTH_SHORT).show();
                }
            });
            View revoke = mNodeSelectorPanel.findViewById(R.id.revoke);
            revoke.setOnClickListener(v->{
                ViewController.revokeLastRule();
            });
            View exchange = mNodeSelectorPanel.findViewById(R.id.exchange);
            View topcentent = mNodeSelectorPanel.findViewById(R.id.topcentent);
            exchange.setOnClickListener(v -> {
                Display display = activity.getWindowManager().getDefaultDisplay();
                int width = display.getWidth();
                int Targetwidth = width - (width / 6);
                if (topcentent.getPaddingRight() == Targetwidth) {
                    topcentent.setPadding(4, 4, 12, 4);
                } else {
                    topcentent.setPadding(4, 4, Targetwidth, 4);
                }
            });
            View btnUp = mNodeSelectorPanel.findViewById(R.id.Up);
            btnUp.setOnClickListener(v -> seekbarreduce());
            View btnDown = mNodeSelectorPanel.findViewById(R.id.Down);
            btnDown.setOnClickListener(v -> seekbaradd());
            container.addView(mNodeSelectorPanel);
            mNodeSelectorPanel.setAlpha(0);
            mNodeSelectorPanel.post(() -> {
                try {
                    mNodeSelectorPanel.setTranslationX(mNodeSelectorPanel.getWidth() / 2.0f);
                    mNodeSelectorPanel.animate()
                            .alpha(1)
                            .translationX(0)
                            .setDuration(300)
                            .setInterpolator(new DecelerateInterpolator(1.0f))
                            .start();
                }catch (Throwable ignore){

                }
            });
            mKeySelecting = true;
        } catch (Throwable e) {
            //god mode package uninstalled?
            Logger.e(TAG, "showNodeSelectPanel fail", e);
            mKeySelecting = false;
        }
    }

    private void seekbaradd() {
        if (seekbar.getProgress() == seekbar.getMax()) {
            return;
        }
        int Progress = seekbar.getProgress() + 1;
        seekbar.setProgress(Progress);
        onProgressChanged(seekbar, Progress, true);
    }

    private void seekbarreduce() {
        if (seekbar.getProgress() == 0) {
            return;
        }
        int Progress = seekbar.getProgress() - 1;
        seekbar.setProgress(Progress);
        onProgressChanged(seekbar, Progress, true);
    }

    private void dismissNodeSelectPanel() {
        if (mMaskView != null) mMaskView.detachFromContainer();
        mMaskView = null;
        if (mNodeSelectorPanel != null) {
            final View nodeSelectorPanel = mNodeSelectorPanel;
            nodeSelectorPanel.post(() -> nodeSelectorPanel.animate()
                    .alpha(0)
                    .translationX(nodeSelectorPanel.getWidth() / 2.0f)
                    .setDuration(250)
                    .setInterpolator(new AccelerateInterpolator(1.0f))
                    .withEndAction(() -> {
                        ViewGroup parent = (ViewGroup) nodeSelectorPanel.getParent();
                        if (parent != null) parent.removeView(nodeSelectorPanel);
                    })
                    .start());
        }
        mNodeSelectorPanel = null;
        mViewNodes.clear();
        mCurrentViewIndex = 0;
        mKeySelecting = false;
    }

    @Override
    public void onPropertyChange(Boolean enable) {
        if (mMaskView != null) {
            dismissNodeSelectPanel();
        }
    }

//    根据process修改选中的view
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            mCurrentViewIndex = progress;
            View view = mViewNodes.get(mCurrentViewIndex).get();
            Logger.d(TAG, String.format(Locale.getDefault(), "progress=%d selected view=%s", progress, view));
            if (view != null) {
                mMaskView.updateOverlayBounds(ViewHelper.getLocationInWindow(view));
            }
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        mNodeSelectorPanel.setAlpha(0.2f);
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        mNodeSelectorPanel.setAlpha(1f);
    }

    public void updateSelectedView(View v){
        int index = -1;
        for (int i = 0; i < mViewNodes.size(); i++) {
            if(mViewNodes.get(i).get() == v){
                index = i;
                break;
            }
        }
        if(index >= 0){
            seekbar.setProgress(index);
            onProgressChanged(seekbar,index,true);
        }
    }


    public void updateViewNodes(){
        mViewNodes.clear();
        mViewNodes.addAll(ViewHelper.buildViewNodes(activity.getWindow().getDecorView()));
        seekbar.setMax(mViewNodes.size() - 1);
    }

}