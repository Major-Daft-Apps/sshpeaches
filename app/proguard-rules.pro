# Production release shrinker rules.

# Pipelined SFTP downloads invoke sshj's protected async READ helpers.
-keepclassmembers class net.schmizz.sshj.sftp.RemoteFile {
    protected net.schmizz.concurrent.Promise asyncRead(long, int);
    protected int checkReadResponse(net.schmizz.sshj.sftp.Response, byte[], int);
}

# SshClientProvider replaces Android's "BC" provider with the bundled Bouncy Castle provider,
# process-wide, on the first SSH connection. That provider registers its algorithms by class-name
# strings and loads them reflectively, so R8 removed the implementations (e.g. PBEPBKDF2), and
# afterwards "PBKDF2WithHmacSHA256" was unavailable to every caller: setting/verifying the PIN,
# and encrypting exports and QR shares. Keep the provider's SPI classes; the crypto engines they
# use are kept through normal reachability.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

# Code editor: TextMate grammars/themes are parsed by reflection (Gson, tm4e) and the Oniguruma port
# (joni/jcodings) loads encodings by class name.
-keep class org.eclipse.tm4e.** { *; }
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
-keep class org.joni.** { *; }
-keep class org.jcodings.** { *; }
-dontwarn org.eclipse.jdt.annotation.**
