package com.kaisar.xposed.godmode;

import com.kaisar.xposed.godmode.rule.ActRules;

interface IObserver {
    oneway void onEditModeChanged(boolean enable);
    oneway void onViewRuleChanged(String packageName, in ActRules actRules);
}
