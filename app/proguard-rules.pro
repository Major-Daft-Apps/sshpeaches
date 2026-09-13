# Production release shrinker rules.

# Pipelined SFTP downloads invoke sshj's protected async READ helpers.
-keepclassmembers class net.schmizz.sshj.sftp.RemoteFile {
    protected net.schmizz.concurrent.Promise asyncRead(long, int);
    protected int checkReadResponse(net.schmizz.sshj.sftp.Response, byte[], int);
}
