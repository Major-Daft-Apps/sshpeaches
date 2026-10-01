package com.majordaftapps.sshpeaches.app.data.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSystemInfoCollectorTest {

    @Test
    fun parseLinuxFastfetchStyleOutput() {
        val raw = """
            UNAME_S=Linux
            UNAME_R=6.8.0-40-generic
            UNAME_M=x86_64
            HOSTNAME=web-1
            PRETTY_NAME=Ubuntu 24.04.1 LTS
            VERSION_ID=24.04
            ID=ubuntu
            CPU=Intel(R) Xeon(R) CPU
            CPU_CORES=4
            MEM_TOTAL_KB=8164304
            MEM_AVAIL_KB=2211840
            DISK=82543000 43210240 35120128 56%
            UPTIME=14:02:11 up 12 days,  3:44,  1 user,  load average: 0.08, 0.03, 0.01
            BEGIN_FREE
                          total        used        free      shared  buff/cache   available
            Mem:           7.8Gi       2.1Gi       512Mi        64Mi       5.2Gi       5.4Gi
            Swap:          2.0Gi          0B       2.0Gi
            END_FREE
            BEGIN_DF
            Filesystem      Size  Used Avail Use% Mounted on
            /dev/sda1        80G   42G   34G  56% /
            /dev/sdb1       500G  120G  355G  26% /data
            /dev/loop0       64M   64M     0 100% /snap/core
            tmpfs           3.9G     0  3.9G   0% /dev/shm
            END_DF
        """.trimIndent()

        val info = HostSystemInfoCollector.parse(raw, nowEpochMillis = 1L)

        assertEquals("Ubuntu 24.04.1 LTS", info.osName)
        assertEquals("24.04", info.osVersion)
        assertEquals("web-1", info.hostname)
        assertEquals("6.8.0-40-generic", info.kernel)
        assertEquals("x86_64", info.architecture)
        assertEquals("Intel(R) Xeon(R) CPU", info.cpu)
        assertEquals("4", info.cpuCores)
        assertTrue(info.freeOutput!!.contains("Mem:"))
        assertTrue(info.freeOutput!!.contains("7.8Gi"))
        assertTrue(info.dfOutput!!.contains("/dev/sda1"))
        assertTrue(info.dfOutput!!.contains("/data"))
        assertFalse(info.dfOutput!!.contains("loop0"))
        assertFalse(info.dfOutput!!.contains("tmpfs"))
        assertTrue(info.hasLiveData)
    }

    @Test
    fun filterPhysicalDfDropsLoopAndTmpfs() {
        val filtered = HostSystemInfoCollector.filterPhysicalDf(
            """
            Filesystem      Size  Used Avail Use% Mounted on
            /dev/nvme0n1p2  900G  400G  450G  48% /
            /dev/loop1       50M   50M     0 100% /snap/foo
            tmpfs           16G     0   16G   0% /run
            """.trimIndent()
        )
        assertEquals(
            """
            Filesystem      Size  Used Avail Use% Mounted on
            /dev/nvme0n1p2  900G  400G  450G  48% /
            """.trimIndent(),
            filtered
        )
    }

    @Test
    fun missingFreeFallsBackToMeminfoSummary() {
        val info = HostSystemInfoCollector.parse(
            """
            MEM_TOTAL_KB=2048
            MEM_AVAIL_KB=1024
            """.trimIndent()
        )
        assertNull(info.freeOutput)
        assertTrue(info.memory!!.contains("available"))
    }

    @Test
    fun formatIecBytesUsesBinaryUnits() {
        assertEquals("1.0 KiB", HostSystemInfoCollector.formatIecBytes(1024))
        assertEquals("1.0 MiB", HostSystemInfoCollector.formatIecBytes(1024L * 1024L))
    }
}
