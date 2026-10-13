# Rules for minifying the release test APK itself. LiveMoshTest calls sshj directly, and
# SSHClient's optional Kerberos login refers to JDK-only classes that Android doesn't have.
-dontwarn javax.security.auth.login.LoginContext
-dontwarn org.ietf.jgss.Oid
