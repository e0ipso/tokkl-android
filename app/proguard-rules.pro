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
