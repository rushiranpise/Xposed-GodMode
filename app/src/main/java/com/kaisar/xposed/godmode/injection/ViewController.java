package com.kaisar.xposed.godmode.injection;

import android.app.Activity;
import android.app.Application;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import com.kaisar.xposed.godmode.injection.bridge.GodModeManager;
import com.kaisar.xposed.godmode.injection.hook.ActivityLifecycleHook;
import com.kaisar.xposed.godmode.injection.util.Logger;
import com.kaisar.xposed.godmode.rule.ViewRule;
import com.kaisar.xposed.godmode.util.Preconditions;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import static com.kaisar.xposed.godmode.GodModeApplication.TAG;

/**
 * Created by jrsen on 17-10-15.
 */

public final class ViewController {
//
    private final static SparseArray<Pair<WeakReference<View>, ViewProperty>> blockedViewCache = new SparseArray<>();
    private final static Stack<ViewRule> editStack = new Stack<>();

// 批量应用规则
    public static void applyRuleBatch(Activity activity, List<ViewRule> rules) {
        Logger.d(TAG, "[ApplyRuleBatch info start------------------------------------]");
        for (ViewRule rule : new ArrayList<>(rules)) {
            try {
                Logger.d(TAG, "[Apply rule]:" + rule.toString());
//                计算规则的哈希值
                int ruleHashCode = rule.hashCode();
                Pair<WeakReference<View>, ViewProperty> viewInfo = blockedViewCache.get(ruleHashCode);
                View view = viewInfo != null ? viewInfo.first.get() : null;
//                空组件或者这个组件没有在当前的窗口
                if (view == null || !view.isAttachedToWindow()) {
//                    更新哈希表
                    blockedViewCache.delete(ruleHashCode);
                    view = ViewHelper.findViewBestMatch(activity, rule);
                    Preconditions.checkNotNull(view, "apply rule fail not match any view");
                }
                boolean blocked = applyRule(view, rule,false);
                if (blocked) {
                    Logger.i(TAG, String.format("[Success] %s#%s has been blocked", activity, view));
                } else {
                    Logger.i(TAG, String.format("[Skipped] %s#%s already be blocked", activity, view));
                }
            } catch (NullPointerException e) {
                Logger.w(TAG, String.format("[Failed] %s#%s block failed because %s", activity, rule.viewClass, e.getMessage()));
            }
        }
        Logger.d(TAG, "[ApplyRuleBatch info end------------------------------------]");
    }
// 根据Rule的规则设置view组建的可见性
    public static boolean applyRule(View v, ViewRule viewRule, boolean fromUser) {
        int ruleHashCode = viewRule.hashCode();
        Pair<WeakReference<View>, ViewProperty> viewInfo = blockedViewCache.get(ruleHashCode);
        View blockedView = viewInfo != null ? viewInfo.first.get() : null;
//        已经被处理过了
        if (blockedView == v && v.getVisibility() == viewRule.visibility) {
            return false;
        }
        ViewProperty viewProperty = blockedView == v ? viewInfo.second : ViewProperty.create(v);
        v.setAlpha(0f);
        v.setClickable(false);
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null) {
            switch (viewRule.visibility) {
                case View.GONE:
                    lp.width = 0;
                    lp.height = 0;
                    break;
                case View.INVISIBLE:
                    lp.width = viewProperty.layout_params_width;
                    lp.height = viewProperty.layout_params_height;
                    break;
            }
            v.requestLayout();
        }
        ViewCompat.setVisibility(v, viewRule.visibility);
//        记录已经被block的view组件
        blockedViewCache.put(ruleHashCode, Pair.create(new WeakReference<>(v), viewProperty));
//        单个操作的情况下
        if(fromUser){
            editStack.push(viewRule);
        }
        Logger.d(TAG, String.format(Locale.getDefault(), "apply rule add view cache %d=%s", ruleHashCode, v));
        Logger.d(TAG, "blockedViewCache:" + blockedViewCache);
        return true;
    }

    public static boolean revokeLastRule(){
        ViewRule last;
        try {
            last = editStack.pop();
        }catch (EmptyStackException e){
            return false;
        }
        int ruleHashCode = last.hashCode();
        Pair<WeakReference<View>, ViewProperty> viewInfo = blockedViewCache.get(ruleHashCode);
        View view = viewInfo != null ? viewInfo.first.get() : null;
        if(view!=null){
            revokeRule(view,last);
        }
        GodModeManager.getDefault().deleteRule(GodModeInjector.loadPackageParam.packageName,last);
        return true;
    }


    public static void revokeRuleBatch(Activity activity, List<ViewRule> rules) {
        for (ViewRule rule : new ArrayList<>(rules)) {
            try {
                Logger.d(TAG, "revoke rule:" + rule.toString());
                int ruleHashCode = rule.hashCode();
                Pair<WeakReference<View>, ViewProperty> viewInfo = blockedViewCache.get(ruleHashCode);
                View view = viewInfo != null ? viewInfo.first.get() : null;
                if (view == null || !view.isAttachedToWindow()) {
                    Logger.w(TAG, "view cache not found");
                    blockedViewCache.delete(ruleHashCode);
                    view = ViewHelper.findViewBestMatch(activity, rule);
                    Logger.w(TAG, "find view in activity" + view);
                    Preconditions.checkNotNull(view, "revoke rule fail can't found block view");
                }
                revokeRule(view, rule);
                Logger.i(TAG, String.format("###revoke rule success [Act]:%s  [View]:%s", activity, view));
            } catch (NullPointerException e) {
                Logger.w(TAG, String.format("###revoke rule fail [Act]:%s  [View]:%s [Reason]:%s", activity, null, e.getMessage()));
            }
        }
    }

    public static void revokeRule(View v, ViewRule viewRule) {
        int ruleHashCode = viewRule.hashCode();
        Pair<WeakReference<View>, ViewProperty> viewInfo = blockedViewCache.get(ruleHashCode);
        if (viewInfo != null && viewInfo.first.get() == v) {
            ViewProperty viewProperty = viewInfo.second;
            v.setAlpha(viewProperty.alpha);
            v.setClickable(viewProperty.clickable);
            ViewCompat.setVisibility(v, viewProperty.visibility);
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null) {
                lp.width = viewProperty.layout_params_width;
                lp.height = viewProperty.layout_params_height;
                v.requestLayout();
            }
            blockedViewCache.delete(viewRule.hashCode());
            Log.e(TAG, String.format(Locale.getDefault(), "revoke blocked view %d=%s %s", ruleHashCode, v, viewProperty));
        } else {
            // cache missing why?
            Log.e(TAG, "view cache missing why?");
            v.setAlpha(1f);
            ViewCompat.setVisibility(v, viewRule.visibility);
        }
    }


    public static void cleanStack(){
        editStack.clear();
    }


    private static final class ViewProperty {

        final float alpha;
        final boolean clickable;
        final int visibility;
        final int layout_params_width;
        final int layout_params_height;

        public ViewProperty(float alpha, boolean clickable, int visibility, int layout_params_width, int layout_params_height) {
            this.alpha = alpha;
            this.clickable = clickable;
            this.visibility = visibility;
            this.layout_params_width = layout_params_width;
            this.layout_params_height = layout_params_height;
        }

        @Override
        public String toString() {
            final StringBuffer sb = new StringBuffer("ViewProperty{");
            sb.append("alpha=").append(alpha);
            sb.append(", clickable=").append(clickable);
            sb.append(", visibility=").append(visibility);
            sb.append(", layout_params_width=").append(layout_params_width);
            sb.append(", layout_params_height=").append(layout_params_height);
            sb.append('}');
            return sb.toString();
        }

        public static ViewProperty create(View view) {
            float alpha = view.getAlpha();
            boolean clickable = view.isClickable();
            int visibility = view.getVisibility();
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            int width = layoutParams != null ? layoutParams.width : 0;
            int height = layoutParams != null ? layoutParams.height : 1;
            return new ViewProperty(alpha, clickable, visibility, width, height);
        }
    }

}
