plugins {
    application
    id("org.jetbrains.kotlin.jvm")
}

application {
    mainClass.set("com.majordaftapps.sshpeaches.livetest.LiveSshServerKt")
}

dependencies {
    implementation("org.apache.sshd:sshd-core:2.12.1")
    implementation("org.apache.sshd:sshd-scp:2.12.1")
    implementation("org.apache.sshd:sshd-sftp:2.12.1")
    implementation("org.slf4j:slf4j-nop:2.0.17")
}
