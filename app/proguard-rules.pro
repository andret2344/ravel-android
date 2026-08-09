# What R8 is told about this app, and - mostly - why it needs to be told almost nothing.
#
# This file is short on purpose, and the shortness is a finding rather than an omission. Every
# library here that needs rules ships them itself as consumer rules, and the app's own code is
# reached from the manifest, from Compose, or from another piece of app code, all of which R8
# follows without help. The rules below are the exceptions, each with the reason it is one.
#
# The temptation with a file like this is to paste in a wall of `-keep` lines from the internet
# "to be safe". That is the opposite of safe: every keep is a piece of the app that stops being
# shrunk, stops being obfuscated and stops being checked, and a keep that turns out to be
# unnecessary is indistinguishable from one that is load-bearing six months later.
#
# ## Room, which arrives here by a route nothing in this app chose
#
# `play-services-ads` depends on `androidx.work:work-runtime:2.7.0`, which depends on
# `androidx.room:room-runtime:2.2.5`. Nothing in Ravel uses either one; they are how the ads SDK
# posts a ping it could not deliver while offline. Room finds the generated implementation of a
# database by building its name - the database class plus `_Impl` - and calling `Class.forName` on
# it, so a renamed class is a class Room cannot find. R8's full mode, on by default since AGP 8,
# renames it.
#
# The symptom is not subtle and does not wait: the app dies on launch, before the first frame, in
# `androidx.startup.InitializationProvider`, with "Failed to create an instance of
# androidx.work.impl.WorkDatabase". It is only invisible in the sense that no debug build and no
# unit test can produce it, which is the entire argument for turning shrinking on early rather than
# on the evening of the first upload.
#
# Room 2.2.5 predates the consumer rules that would have covered this. Upgrading Room directly is
# not the fix - it is pinned by a transitive dependency this app does not declare - so the rule
# lives here.
#
# Re-tested rather than assumed whenever the ads SDK moves, and "re-tested" means installing a
# release build and opening it, because nothing else shows this. Last checked against
# play-services-ads 25.4.0 and user-messaging-platform 4.0.0, which still pin work-runtime 2.7.0 and
# room-runtime 2.2.5 - the ads SDK crossed a major version without moving either, so the rule is
# still doing work.
#
# Android Studio marks `androidx.room.RoomDatabase` on the line below as an unresolved class name.
# It resolves for R8, which is the only reader that matters. The IDE checks this file against the
# app's *compile* classpath, and Room is on the *runtime* one only, precisely because nothing here
# declares it - it arrives under play-services-ads, which is the whole point of the paragraphs
# above. `:app:dependencies --configuration releaseRuntimeClasspath` shows it. Do not "fix" the
# warning by deleting the rule: a keep naming a class R8 cannot find fails quietly, keeping nothing
# rather than failing the build, and the symptom is the launch crash described above.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
#
# ## What was checked and found to need nothing
#
# - **play-services-ads and user-messaging-platform themselves.** Both ship consumer rules covering
#   their own reflection. Nothing in this app reflects on them.
# - **AndroidX Lifecycle view models.** `viewModel()` builds an `AndroidViewModel` by reflecting on
#   its `(Application)` constructor, which would be exactly the sort of thing to break here - but
#   lifecycle-viewmodel already keeps those constructors through its own consumer rules.
# - **Compose.** Composable functions are called from generated code in the same compilation unit,
#   so R8 sees the edges.
# - **`:generator`.** Plain Kotlin with no reflection and no service loading. Its dictionaries are
#   Java resources read by name through the classloader, and Java resources are not touched by code
#   shrinking; the Android resource shrinker only ever looks at `res/`.
# - **`org.json`.** Provided by the platform, and used through its normal API - `SavedBoards` reads
#   and writes fields by name, not by reflecting on a class.
#
# ## Diagnostics
#
# Line numbers are kept and the source file name is replaced with a constant, which is what makes a
# stack trace from Play Console retraceable against the mapping file while still telling an attacker
# nothing about the original file names. Without the first line, a crash report from a release build
# is a list of method names with no line numbers, and the mapping file cannot put them back.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
