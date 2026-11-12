# No serialization / reflection-heavy libraries: nothing to keep.
# The foreground service, its actions and the custom WireGuard permission
# are referenced from the manifest and from explicit Intent actions.
-keep class com.autoscribe.android.TestService { *; }
