# Markwon (markdown → spans; keep model classes used via CommonMark / plugins)
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# CommonMark node graph (Markwon parser / visitors)
-keep class org.commonmark.** { *; }
-dontwarn org.commonmark.**

# JLatexMath (Markwon ext-latex → ru.noties.jlatexmath)
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn ru.noties.jlatexmath.**

# JLaTeXMath core (reflection / macro packages; R8 breaks NewCommandMacro without this)
-keep class org.scilab.forge.jlatexmath.** { *; }
-keepclassmembers class org.scilab.forge.jlatexmath.** { <init>(...); }
-dontwarn org.scilab.forge.jlatexmath.**

# Coil (Markwon image-coil async drawable loading)
-keep class coil.** { *; }
-dontwarn coil.**

# OkHttp / Okio (Coil network stack; R8 may not see full usage)
-dontwarn okhttp3.**
-dontwarn okio.**
