package com.majordaftapps.sshpeaches.app.service

/**
 * The last [capacity] folders a file browser listed, by path, newest kept, plus its home folder, which is kept
 * apart and never pushed out: going back, forward, up or home to one of them shows it at once while the folder is
 * listed again.
 */
internal class RecentListings<T>(private val capacity: Int = 5) {
    private val byPath = object : LinkedHashMap<String, T>(capacity + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, T>) = size > capacity
    }
    private var homePath: String? = null
    private var home: T? = null

    /** [path]'s listing; "." (where the browser starts) is the home folder. */
    @Synchronized
    fun get(path: String): T? {
        val key = key(path)
        if (key == "." || key == homePath) return home
        return byPath[key]
    }

    @Synchronized
    fun put(path: String, listing: T) {
        val key = key(path)
        if (key == homePath) home = listing else byPath[key] = listing
    }

    /** The home folder's listing, at [path] (the folder "." resolved to). */
    @Synchronized
    fun putHome(path: String, listing: T) {
        val key = key(path)
        byPath.remove(key)
        homePath = key
        home = listing
    }

    @Synchronized
    fun paths(): List<String> = byPath.keys.toList()

    companion object {
        /** "/srv/data/" and "/srv/data" are one folder; "/" stays "/". */
        fun key(path: String): String = path.trim().trimEnd('/').ifEmpty { if (path.trim().startsWith("/")) "/" else "." }
    }
}
