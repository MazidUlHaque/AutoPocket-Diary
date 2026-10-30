# SQLCipher calls into these classes from native code.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**
