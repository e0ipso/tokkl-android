# Glance persists each widget implementation's canonical name in its provider
# registry. Keep those names stable across updates from unoptimized releases.
-keepnames class com.github.e0ipso.tokkl.widget.TokklWidget
-keepnames class com.github.e0ipso.tokkl.widget.PomodoroWidget

# Glance creates ActionCallback instances through their public no-arg
# constructors. Its consumer rule retains the classes but R8 removes these
# constructors unless they are retained explicitly.
-keepclassmembers class com.github.e0ipso.tokkl.widget.* implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# R8 9.5.28 (AGP 9.5.0) gives emitAbort, a private suspend function of
# Flow.take(), a Continuation or a ContinuationImpl last parameter, picking one
# or the other from run to run with the same inputs, even on one thread.
# classes.dex, and with it baseline.prof, then differ between two builds of one
# commit, and scripts/reproducible-release.sh fails. Keeping the signatures of
# emitAbort and of its synthetic accessor, the one caller, stops R8 from
# changing that parameter type.
-keepclassmembers,allowobfuscation,allowshrinking class kotlinx.coroutines.flow.FlowKt__LimitKt {
    static java.lang.Object *emitAbort$FlowKt__LimitKt(kotlinx.coroutines.flow.FlowCollector, java.lang.Object, java.lang.Object, kotlin.coroutines.Continuation);
}
