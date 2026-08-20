#define _GNU_SOURCE
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <spawn.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

extern char **environ;

static __thread int sb_access_guard = 0;

static const char *sb_basename(const char *path) {
    const char *slash;
    if (path == NULL) {
        return "";
    }
    slash = strrchr(path, '/');
    return slash == NULL ? path : slash + 1;
}

static const char *sb_resolved_basename(const char *path, char *resolved, size_t resolved_size) {
    const char *path_env;
    char *path_copy;
    char *saveptr = NULL;
    char *part;
    if (path == NULL) {
        return NULL;
    }
    if (strchr(path, '/') != NULL) {
        return realpath(path, resolved) == NULL ? NULL : sb_basename(resolved);
    }
    path_env = getenv("PATH");
    if (path_env == NULL) {
        return NULL;
    }
    path_copy = strdup(path_env);
    if (path_copy == NULL) {
        return NULL;
    }
    for (part = strtok_r(path_copy, ":", &saveptr); part != NULL; part = strtok_r(NULL, ":", &saveptr)) {
        char candidate[PATH_MAX];
        const char *dir = part[0] == '\0' ? "." : part;
        int written = snprintf(candidate, sizeof(candidate), "%s/%s", dir, path);
        if (written < 0 || (size_t)written >= sizeof(candidate)) {
            continue;
        }
        if (access(candidate, X_OK) == 0 && realpath(candidate, resolved) != NULL) {
            const char *base = sb_basename(resolved);
            free(path_copy);
            return base;
        }
    }
    free(path_copy);
    (void)resolved_size;
    return NULL;
}

static int sb_list_contains(const char *list, const char *needle) {
    const char *start;
    size_t needle_len;
    if (list == NULL || needle == NULL || needle[0] == '\0') {
        return 0;
    }
    needle_len = strlen(needle);
    start = list;
    while (*start != '\0') {
        const char *end = strchr(start, ',');
        size_t len = end == NULL ? strlen(start) : (size_t)(end - start);
        while (len > 0 && (*start == ' ' || *start == '\t')) {
            start++;
            len--;
        }
        while (len > 0 && (start[len - 1] == ' ' || start[len - 1] == '\t')) {
            len--;
        }
        if (len == needle_len && strncmp(start, needle, len) == 0) {
            return 1;
        }
        if (end == NULL) {
            break;
        }
        start = end + 1;
    }
    return 0;
}

static const char *sb_git_subcommand(char *const argv[]) {
    int idx = 1;
    if (argv == NULL) {
        return NULL;
    }
    while (argv[idx] != NULL) {
        const char *arg = argv[idx];
        if (
            strcmp(arg, "-C") == 0 ||
            strcmp(arg, "-c") == 0 ||
            strcmp(arg, "--git-dir") == 0 ||
            strcmp(arg, "--work-tree") == 0 ||
            strcmp(arg, "--namespace") == 0 ||
            strcmp(arg, "--config-env") == 0
        ) {
            idx += argv[idx + 1] == NULL ? 1 : 2;
            continue;
        }
        if (
            strncmp(arg, "--git-dir=", 10) == 0 ||
            strncmp(arg, "--work-tree=", 12) == 0 ||
            strncmp(arg, "--namespace=", 12) == 0 ||
            strncmp(arg, "--config-env=", 13) == 0 ||
            (strncmp(arg, "-c", 2) == 0 && strcmp(arg, "-c") != 0)
        ) {
            idx++;
            continue;
        }
        if (strcmp(arg, "--exec-path") == 0) {
            idx += argv[idx + 1] != NULL && argv[idx + 1][0] != '-' ? 2 : 1;
            continue;
        }
        if (
            strncmp(arg, "--exec-path=", 12) == 0 ||
            strcmp(arg, "--no-pager") == 0 ||
            strcmp(arg, "--paginate") == 0 ||
            strcmp(arg, "-p") == 0 ||
            strcmp(arg, "--bare") == 0 ||
            strcmp(arg, "--no-replace-objects") == 0 ||
            strcmp(arg, "--literal-pathspecs") == 0 ||
            strcmp(arg, "--glob-pathspecs") == 0 ||
            strcmp(arg, "--noglob-pathspecs") == 0 ||
            strcmp(arg, "--icase-pathspecs") == 0 ||
            strcmp(arg, "--no-optional-locks") == 0
        ) {
            idx++;
            continue;
        }
        if (strcmp(arg, "--") == 0) {
            return argv[idx + 1];
        }
        if (arg[0] == '-') {
            idx++;
            continue;
        }
        return arg;
    }
    return NULL;
}

static int sb_should_block(const char *path, char *const argv[]) {
    const char *base = sb_basename(path);
    char resolved[PATH_MAX];
    const char *resolved_base = sb_resolved_basename(path, resolved, sizeof(resolved));
    const char *denied_commands = getenv("STRINGBEAN_DENIED_COMMANDS");
    const char *denied_git = getenv("STRINGBEAN_DENIED_GIT_SUBCOMMANDS");
    if (sb_list_contains(denied_commands, base) || sb_list_contains(denied_commands, resolved_base)) {
        const char *denied = sb_list_contains(denied_commands, base) ? base : resolved_base;
        fprintf(stderr, "stringbean policy: command '%s' is denied for subagents.\n", denied);
        return 1;
    }
    if (strcmp(base, "git") == 0 || (resolved_base != NULL && strcmp(resolved_base, "git") == 0)) {
        const char *subcommand = sb_git_subcommand(argv);
        if (sb_list_contains(denied_git, subcommand)) {
            fprintf(stderr, "stringbean policy: this git operation is denied for subagents: git %s\n", subcommand);
            return 1;
        }
    }
    if (strncmp(base, "git-", 4) == 0 && sb_list_contains(denied_git, base + 4)) {
        fprintf(stderr, "stringbean policy: this git operation is denied for subagents: git %s\n", base + 4);
        return 1;
    }
    if (resolved_base != NULL && strncmp(resolved_base, "git-", 4) == 0 && sb_list_contains(denied_git, resolved_base + 4)) {
        fprintf(stderr, "stringbean policy: this git operation is denied for subagents: git %s\n", resolved_base + 4);
        return 1;
    }
    return 0;
}

static int sb_absolute_path(const char *path, int dirfd, char *output, size_t output_size) {
    char combined[PATH_MAX];
    char base[PATH_MAX];
    char parent[PATH_MAX];
    char resolved_parent[PATH_MAX];
    char proc_path[64];
    char *slash;
    ssize_t base_len;
    int written;
    if (path == NULL || path[0] == '\0') {
        return 0;
    }
    if (path[0] == '/') {
        written = snprintf(combined, sizeof(combined), "%s", path);
    } else {
        if (dirfd == AT_FDCWD) {
            if (getcwd(base, sizeof(base)) == NULL) {
                return 0;
            }
        } else {
            written = snprintf(proc_path, sizeof(proc_path), "/proc/self/fd/%d", dirfd);
            if (written < 0 || (size_t)written >= sizeof(proc_path)) {
                return 0;
            }
            base_len = readlink(proc_path, base, sizeof(base) - 1);
            if (base_len < 0 || (size_t)base_len >= sizeof(base) - 1) {
                return 0;
            }
            base[base_len] = '\0';
        }
        written = snprintf(combined, sizeof(combined), "%s/%s", base, path);
    }
    if (written < 0 || (size_t)written >= sizeof(combined)) {
        return 0;
    }
    if (realpath(combined, output) != NULL) {
        return 1;
    }
    written = snprintf(parent, sizeof(parent), "%s", combined);
    if (written < 0 || (size_t)written >= sizeof(parent)) {
        return 0;
    }
    slash = strrchr(parent, '/');
    if (slash != NULL && slash != parent) {
        const char *name = slash + 1;
        *slash = '\0';
        if (realpath(parent, resolved_parent) != NULL) {
            written = snprintf(output, output_size, "%s/%s", resolved_parent, name);
            return written >= 0 && (size_t)written < output_size;
        }
    }
    written = snprintf(output, output_size, "%s", combined);
    return written >= 0 && (size_t)written < output_size;
}

static int sb_path_is_within(const char *root, const char *path) {
    size_t root_len;
    if (root == NULL || root[0] == '\0' || path == NULL || path[0] == '\0') {
        return 0;
    }
    root_len = strlen(root);
    while (root_len > 1 && root[root_len - 1] == '/') {
        root_len--;
    }
    return strncmp(root, path, root_len) == 0 &&
        (path[root_len] == '\0' || path[root_len] == '/');
}

static int sb_write_policy_enabled(void) {
    const char *mode = getenv("STRINGBEAN_POLICY_WRITE_MODE");
    return mode != NULL && strcmp(mode, "read-only") == 0;
}

static int sb_should_block_write(const char *path, int dirfd) {
    char absolute[PATH_MAX];
    const char *workspace_root;
    int blocked;
    if (sb_access_guard || !sb_write_policy_enabled()) {
        return 0;
    }
    workspace_root = getenv("STRINGBEAN_POLICY_WORKSPACE_ROOT");
    if (workspace_root == NULL || workspace_root[0] == '\0') {
        return 0;
    }
    sb_access_guard = 1;
    blocked = sb_absolute_path(path, dirfd, absolute, sizeof(absolute)) &&
        sb_path_is_within(workspace_root, absolute);
    sb_access_guard = 0;
    if (blocked) {
        fprintf(stderr, "stringbean policy: read-only workspace write denied: %s\n", absolute);
    }
    return blocked;
}

static int sb_should_block_fd_write(int fd) {
    char proc_path[64];
    int written = snprintf(proc_path, sizeof(proc_path), "/proc/self/fd/%d", fd);
    if (written < 0 || (size_t)written >= sizeof(proc_path)) {
        return 0;
    }
    return sb_should_block_write(proc_path, AT_FDCWD);
}

static int sb_flags_request_write(int flags) {
    return (flags & (O_WRONLY | O_RDWR | O_CREAT | O_TRUNC | O_APPEND)) != 0;
}

static int sb_fopen_requests_write(const char *mode) {
    return mode != NULL && strpbrk(mode, "wax+") != NULL;
}

static int sb_path_list_contains(const char *list, const char *path) {
    const char *start;
    size_t path_len;
    if (list == NULL || list[0] == '\0' || path == NULL || path[0] == '\0') {
        return 0;
    }
    path_len = strlen(path);
    start = list;
    while (*start != '\0') {
        const char *end = strchr(start, 0x1f);
        size_t len = end == NULL ? strlen(start) : (size_t)(end - start);
        if (
            len > 0 &&
            path_len >= len &&
            strncmp(start, path, len) == 0 &&
            (path_len == len || path[len] == '/')
        ) {
            return 1;
        }
        if (end == NULL) {
            break;
        }
        start = end + 1;
    }
    return 0;
}

static int sb_should_block_access(const char *path, int dirfd) {
    char absolute[PATH_MAX];
    const char *allowed_paths;
    const char *excluded_paths;
    int blocked;
    if (sb_access_guard) {
        return 0;
    }
    excluded_paths = getenv("STRINGBEAN_POLICY_EXCLUDED_PATHS");
    if (excluded_paths == NULL || excluded_paths[0] == '\0') {
        return 0;
    }
    sb_access_guard = 1;
    allowed_paths = getenv("STRINGBEAN_POLICY_ALLOWED_PATHS");
    blocked = sb_absolute_path(path, dirfd, absolute, sizeof(absolute)) &&
        !sb_path_list_contains(allowed_paths, absolute) &&
        sb_path_list_contains(excluded_paths, absolute);
    sb_access_guard = 0;
    if (blocked) {
        fprintf(stderr, "stringbean policy: excluded path access denied; skip it without retrying.\n");
    }
    return blocked;
}

static int sb_open_needs_mode(int flags) {
    if ((flags & O_CREAT) != 0) {
        return 1;
    }
#ifdef O_TMPFILE
    if ((flags & O_TMPFILE) == O_TMPFILE) {
        return 1;
    }
#endif
    return 0;
}

int open(const char *pathname, int flags, ...) {
    static int (*real_open)(const char *, int, ...) = NULL;
    mode_t mode = 0;
    if (sb_open_needs_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }
    if (sb_should_block_access(pathname, AT_FDCWD) ||
        (sb_flags_request_write(flags) && sb_should_block_write(pathname, AT_FDCWD))) {
        errno = EACCES;
        return -1;
    }
    if (real_open == NULL) {
        real_open = dlsym(RTLD_NEXT, "open");
    }
    return sb_open_needs_mode(flags) ? real_open(pathname, flags, mode) : real_open(pathname, flags);
}

int open64(const char *pathname, int flags, ...) {
    static int (*real_open64)(const char *, int, ...) = NULL;
    mode_t mode = 0;
    if (sb_open_needs_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }
    if (sb_should_block_access(pathname, AT_FDCWD) ||
        (sb_flags_request_write(flags) && sb_should_block_write(pathname, AT_FDCWD))) {
        errno = EACCES;
        return -1;
    }
    if (real_open64 == NULL) {
        real_open64 = dlsym(RTLD_NEXT, "open64");
    }
    return sb_open_needs_mode(flags) ? real_open64(pathname, flags, mode) : real_open64(pathname, flags);
}

int openat(int dirfd, const char *pathname, int flags, ...) {
    static int (*real_openat)(int, const char *, int, ...) = NULL;
    mode_t mode = 0;
    if (sb_open_needs_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }
    if (sb_should_block_access(pathname, dirfd) ||
        (sb_flags_request_write(flags) && sb_should_block_write(pathname, dirfd))) {
        errno = EACCES;
        return -1;
    }
    if (real_openat == NULL) {
        real_openat = dlsym(RTLD_NEXT, "openat");
    }
    return sb_open_needs_mode(flags) ? real_openat(dirfd, pathname, flags, mode) : real_openat(dirfd, pathname, flags);
}

int openat64(int dirfd, const char *pathname, int flags, ...) {
    static int (*real_openat64)(int, const char *, int, ...) = NULL;
    mode_t mode = 0;
    if (sb_open_needs_mode(flags)) {
        va_list args;
        va_start(args, flags);
        mode = va_arg(args, mode_t);
        va_end(args);
    }
    if (sb_should_block_access(pathname, dirfd) ||
        (sb_flags_request_write(flags) && sb_should_block_write(pathname, dirfd))) {
        errno = EACCES;
        return -1;
    }
    if (real_openat64 == NULL) {
        real_openat64 = dlsym(RTLD_NEXT, "openat64");
    }
    return sb_open_needs_mode(flags) ? real_openat64(dirfd, pathname, flags, mode) : real_openat64(dirfd, pathname, flags);
}

FILE *fopen(const char *pathname, const char *mode) {
    static FILE *(*real_fopen)(const char *, const char *) = NULL;
    if (sb_should_block_access(pathname, AT_FDCWD) ||
        (sb_fopen_requests_write(mode) && sb_should_block_write(pathname, AT_FDCWD))) {
        errno = EACCES;
        return NULL;
    }
    if (real_fopen == NULL) {
        real_fopen = dlsym(RTLD_NEXT, "fopen");
    }
    return real_fopen(pathname, mode);
}

FILE *fopen64(const char *pathname, const char *mode) {
    static FILE *(*real_fopen64)(const char *, const char *) = NULL;
    if (sb_should_block_access(pathname, AT_FDCWD) ||
        (sb_fopen_requests_write(mode) && sb_should_block_write(pathname, AT_FDCWD))) {
        errno = EACCES;
        return NULL;
    }
    if (real_fopen64 == NULL) {
        real_fopen64 = dlsym(RTLD_NEXT, "fopen64");
    }
    return real_fopen64(pathname, mode);
}

int creat(const char *pathname, mode_t mode) {
    static int (*real_creat)(const char *, mode_t) = NULL;
    if (sb_should_block_write(pathname, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_creat == NULL) {
        real_creat = dlsym(RTLD_NEXT, "creat");
    }
    return real_creat(pathname, mode);
}

int creat64(const char *pathname, mode_t mode) {
    static int (*real_creat64)(const char *, mode_t) = NULL;
    if (sb_should_block_write(pathname, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_creat64 == NULL) {
        real_creat64 = dlsym(RTLD_NEXT, "creat64");
    }
    return real_creat64(pathname, mode);
}

int mkdir(const char *pathname, mode_t mode) {
    static int (*real_mkdir)(const char *, mode_t) = NULL;
    if (sb_should_block_write(pathname, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_mkdir == NULL) {
        real_mkdir = dlsym(RTLD_NEXT, "mkdir");
    }
    return real_mkdir(pathname, mode);
}

int mkdirat(int dirfd, const char *pathname, mode_t mode) {
    static int (*real_mkdirat)(int, const char *, mode_t) = NULL;
    if (sb_should_block_write(pathname, dirfd)) {
        errno = EACCES;
        return -1;
    }
    if (real_mkdirat == NULL) {
        real_mkdirat = dlsym(RTLD_NEXT, "mkdirat");
    }
    return real_mkdirat(dirfd, pathname, mode);
}

int unlink(const char *pathname) {
    static int (*real_unlink)(const char *) = NULL;
    if (sb_should_block_write(pathname, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_unlink == NULL) {
        real_unlink = dlsym(RTLD_NEXT, "unlink");
    }
    return real_unlink(pathname);
}

int unlinkat(int dirfd, const char *pathname, int flags) {
    static int (*real_unlinkat)(int, const char *, int) = NULL;
    if (sb_should_block_write(pathname, dirfd)) {
        errno = EACCES;
        return -1;
    }
    if (real_unlinkat == NULL) {
        real_unlinkat = dlsym(RTLD_NEXT, "unlinkat");
    }
    return real_unlinkat(dirfd, pathname, flags);
}

int rmdir(const char *pathname) {
    static int (*real_rmdir)(const char *) = NULL;
    if (sb_should_block_write(pathname, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_rmdir == NULL) {
        real_rmdir = dlsym(RTLD_NEXT, "rmdir");
    }
    return real_rmdir(pathname);
}

int rename(const char *oldpath, const char *newpath) {
    static int (*real_rename)(const char *, const char *) = NULL;
    if (sb_should_block_write(oldpath, AT_FDCWD) || sb_should_block_write(newpath, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_rename == NULL) {
        real_rename = dlsym(RTLD_NEXT, "rename");
    }
    return real_rename(oldpath, newpath);
}

int renameat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath) {
    static int (*real_renameat)(int, const char *, int, const char *) = NULL;
    if (sb_should_block_write(oldpath, olddirfd) || sb_should_block_write(newpath, newdirfd)) {
        errno = EACCES;
        return -1;
    }
    if (real_renameat == NULL) {
        real_renameat = dlsym(RTLD_NEXT, "renameat");
    }
    return real_renameat(olddirfd, oldpath, newdirfd, newpath);
}

int renameat2(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, unsigned int flags) {
    static int (*real_renameat2)(int, const char *, int, const char *, unsigned int) = NULL;
    if (sb_should_block_write(oldpath, olddirfd) || sb_should_block_write(newpath, newdirfd)) {
        errno = EACCES;
        return -1;
    }
    if (real_renameat2 == NULL) {
        real_renameat2 = dlsym(RTLD_NEXT, "renameat2");
    }
    if (real_renameat2 == NULL) {
        errno = ENOSYS;
        return -1;
    }
    return real_renameat2(olddirfd, oldpath, newdirfd, newpath, flags);
}

int link(const char *oldpath, const char *newpath) {
    static int (*real_link)(const char *, const char *) = NULL;
    if (sb_should_block_write(newpath, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_link == NULL) {
        real_link = dlsym(RTLD_NEXT, "link");
    }
    return real_link(oldpath, newpath);
}

int linkat(int olddirfd, const char *oldpath, int newdirfd, const char *newpath, int flags) {
    static int (*real_linkat)(int, const char *, int, const char *, int) = NULL;
    if (sb_should_block_write(newpath, newdirfd)) {
        errno = EACCES;
        return -1;
    }
    if (real_linkat == NULL) {
        real_linkat = dlsym(RTLD_NEXT, "linkat");
    }
    return real_linkat(olddirfd, oldpath, newdirfd, newpath, flags);
}

int symlink(const char *target, const char *linkpath) {
    static int (*real_symlink)(const char *, const char *) = NULL;
    if (sb_should_block_write(linkpath, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_symlink == NULL) {
        real_symlink = dlsym(RTLD_NEXT, "symlink");
    }
    return real_symlink(target, linkpath);
}

int symlinkat(const char *target, int newdirfd, const char *linkpath) {
    static int (*real_symlinkat)(const char *, int, const char *) = NULL;
    if (sb_should_block_write(linkpath, newdirfd)) {
        errno = EACCES;
        return -1;
    }
    if (real_symlinkat == NULL) {
        real_symlinkat = dlsym(RTLD_NEXT, "symlinkat");
    }
    return real_symlinkat(target, newdirfd, linkpath);
}

int truncate(const char *path, off_t length) {
    static int (*real_truncate)(const char *, off_t) = NULL;
    if (sb_should_block_write(path, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_truncate == NULL) {
        real_truncate = dlsym(RTLD_NEXT, "truncate");
    }
    return real_truncate(path, length);
}

int truncate64(const char *path, off64_t length) {
    static int (*real_truncate64)(const char *, off64_t) = NULL;
    if (sb_should_block_write(path, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_truncate64 == NULL) {
        real_truncate64 = dlsym(RTLD_NEXT, "truncate64");
    }
    return real_truncate64(path, length);
}

int ftruncate(int fd, off_t length) {
    static int (*real_ftruncate)(int, off_t) = NULL;
    if (sb_should_block_fd_write(fd)) {
        errno = EACCES;
        return -1;
    }
    if (real_ftruncate == NULL) {
        real_ftruncate = dlsym(RTLD_NEXT, "ftruncate");
    }
    return real_ftruncate(fd, length);
}

int ftruncate64(int fd, off64_t length) {
    static int (*real_ftruncate64)(int, off64_t) = NULL;
    if (sb_should_block_fd_write(fd)) {
        errno = EACCES;
        return -1;
    }
    if (real_ftruncate64 == NULL) {
        real_ftruncate64 = dlsym(RTLD_NEXT, "ftruncate64");
    }
    return real_ftruncate64(fd, length);
}

int chmod(const char *path, mode_t mode) {
    static int (*real_chmod)(const char *, mode_t) = NULL;
    if (sb_should_block_write(path, AT_FDCWD)) {
        errno = EACCES;
        return -1;
    }
    if (real_chmod == NULL) {
        real_chmod = dlsym(RTLD_NEXT, "chmod");
    }
    return real_chmod(path, mode);
}

int fchmod(int fd, mode_t mode) {
    static int (*real_fchmod)(int, mode_t) = NULL;
    if (sb_should_block_fd_write(fd)) {
        errno = EACCES;
        return -1;
    }
    if (real_fchmod == NULL) {
        real_fchmod = dlsym(RTLD_NEXT, "fchmod");
    }
    return real_fchmod(fd, mode);
}

int fchmodat(int dirfd, const char *path, mode_t mode, int flags) {
    static int (*real_fchmodat)(int, const char *, mode_t, int) = NULL;
    if (sb_should_block_write(path, dirfd)) {
        errno = EACCES;
        return -1;
    }
    if (real_fchmodat == NULL) {
        real_fchmodat = dlsym(RTLD_NEXT, "fchmodat");
    }
    return real_fchmodat(dirfd, path, mode, flags);
}

DIR *opendir(const char *name) {
    static DIR *(*real_opendir)(const char *) = NULL;
    if (sb_should_block_access(name, AT_FDCWD)) {
        errno = EACCES;
        return NULL;
    }
    if (real_opendir == NULL) {
        real_opendir = dlsym(RTLD_NEXT, "opendir");
    }
    return real_opendir(name);
}

static const char *const sb_preserved_env_names[] = {
    "LD_PRELOAD",
    "STRINGBEAN_ACTIVE_CHILD",
    "STRINGBEAN_POLICY_BIN",
    "STRINGBEAN_POLICY_PRELOAD",
    "STRINGBEAN_POLICY_PRELOAD_ACTIVE",
    "STRINGBEAN_POLICY_WRAPPERS_ACTIVE",
    "STRINGBEAN_POLICY_EXCLUDED_PATHS",
    "STRINGBEAN_POLICY_ALLOWED_PATHS",
    "STRINGBEAN_POLICY_WORKSPACE_ROOT",
    "STRINGBEAN_POLICY_WRITE_MODE",
    "STRINGBEAN_DENIED_COMMANDS",
    "STRINGBEAN_DENIED_GIT_SUBCOMMANDS",
    NULL
};

static int sb_env_entry_has_name(const char *entry, const char *name) {
    size_t len;
    if (entry == NULL || name == NULL) {
        return 0;
    }
    len = strlen(name);
    return strncmp(entry, name, len) == 0 && entry[len] == '=';
}

static int sb_is_preserved_env_entry(const char *entry) {
    size_t idx;
    for (idx = 0; sb_preserved_env_names[idx] != NULL; idx++) {
        if (sb_env_entry_has_name(entry, sb_preserved_env_names[idx])) {
            return 1;
        }
    }
    return 0;
}

static void sb_free_hardened_env(
    char **environment,
    size_t appended_start,
    size_t appended_count
) {
    size_t idx;
    if (environment == NULL) {
        return;
    }
    for (idx = 0; idx < appended_count; idx++) {
        free(environment[appended_start + idx]);
    }
    free(environment);
}

static int sb_harden_child_env(
    char *const envp[],
    char ***output,
    size_t *appended_start,
    size_t *appended_count
) {
    const char *policy_preload = getenv("STRINGBEAN_POLICY_PRELOAD");
    size_t input_count = 0;
    size_t preserved_count = 0;
    size_t kept_count = 0;
    size_t idx;
    char **hardened;
    if (policy_preload == NULL || policy_preload[0] == '\0') {
        *output = NULL;
        *appended_start = 0;
        *appended_count = 0;
        return 0;
    }
    if (envp != NULL) {
        while (envp[input_count] != NULL) {
            input_count++;
        }
    }
    for (idx = 0; sb_preserved_env_names[idx] != NULL; idx++) {
        const char *value = getenv(sb_preserved_env_names[idx]);
        if (value != NULL) {
            preserved_count++;
        }
    }
    hardened = calloc(input_count + preserved_count + 1, sizeof(char *));
    if (hardened == NULL) {
        return -1;
    }
    for (idx = 0; idx < input_count; idx++) {
        if (!sb_is_preserved_env_entry(envp[idx])) {
            hardened[kept_count++] = envp[idx];
        }
    }
    *appended_start = kept_count;
    *appended_count = 0;
    for (idx = 0; sb_preserved_env_names[idx] != NULL; idx++) {
        const char *name = sb_preserved_env_names[idx];
        const char *value = getenv(name);
        size_t length;
        char *entry;
        if (value == NULL) {
            continue;
        }
        length = strlen(name) + strlen(value) + 2;
        entry = malloc(length);
        if (entry == NULL) {
            sb_free_hardened_env(hardened, *appended_start, *appended_count);
            *output = NULL;
            return -1;
        }
        snprintf(entry, length, "%s=%s", name, value);
        hardened[kept_count++] = entry;
        (*appended_count)++;
    }
    hardened[kept_count] = NULL;
    *output = hardened;
    return 1;
}

int execve(const char *pathname, char *const argv[], char *const envp[]) {
    static int (*real_execve)(const char *, char *const[], char *const[]) = NULL;
    char **hardened_env = NULL;
    size_t appended_start = 0;
    size_t appended_count = 0;
    int harden_result;
    int result;
    if (sb_should_block(pathname, argv)) {
        errno = EACCES;
        return -1;
    }
    if (real_execve == NULL) {
        real_execve = dlsym(RTLD_NEXT, "execve");
    }
    harden_result = sb_harden_child_env(envp, &hardened_env, &appended_start, &appended_count);
    if (harden_result < 0) {
        errno = ENOMEM;
        return -1;
    }
    result = real_execve(pathname, argv, harden_result > 0 ? hardened_env : envp);
    sb_free_hardened_env(hardened_env, appended_start, appended_count);
    return result;
}

int execv(const char *path, char *const argv[]) {
    static int (*real_execv)(const char *, char *const[]) = NULL;
    if (sb_should_block(path, argv)) {
        errno = EACCES;
        return -1;
    }
    if (real_execv == NULL) {
        real_execv = dlsym(RTLD_NEXT, "execv");
    }
    return real_execv(path, argv);
}

int execvp(const char *file, char *const argv[]) {
    static int (*real_execvp)(const char *, char *const[]) = NULL;
    if (sb_should_block(file, argv)) {
        errno = EACCES;
        return -1;
    }
    if (real_execvp == NULL) {
        real_execvp = dlsym(RTLD_NEXT, "execvp");
    }
    return real_execvp(file, argv);
}

int execvpe(const char *file, char *const argv[], char *const envp[]) {
    static int (*real_execvpe)(const char *, char *const[], char *const[]) = NULL;
    char **hardened_env = NULL;
    size_t appended_start = 0;
    size_t appended_count = 0;
    int harden_result;
    int result;
    if (sb_should_block(file, argv)) {
        errno = EACCES;
        return -1;
    }
    if (real_execvpe == NULL) {
        real_execvpe = dlsym(RTLD_NEXT, "execvpe");
    }
    harden_result = sb_harden_child_env(envp, &hardened_env, &appended_start, &appended_count);
    if (harden_result < 0) {
        errno = ENOMEM;
        return -1;
    }
    result = real_execvpe(file, argv, harden_result > 0 ? hardened_env : envp);
    sb_free_hardened_env(hardened_env, appended_start, appended_count);
    return result;
}

int posix_spawn(
    pid_t *pid,
    const char *path,
    const posix_spawn_file_actions_t *file_actions,
    const posix_spawnattr_t *attrp,
    char *const argv[],
    char *const envp[]
) {
    static int (*real_posix_spawn)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *, char *const[], char *const[]) = NULL;
    char **hardened_env = NULL;
    size_t appended_start = 0;
    size_t appended_count = 0;
    int harden_result;
    int result;
    if (sb_should_block(path, argv)) {
        return EACCES;
    }
    if (real_posix_spawn == NULL) {
        real_posix_spawn = dlsym(RTLD_NEXT, "posix_spawn");
    }
    harden_result = sb_harden_child_env(envp, &hardened_env, &appended_start, &appended_count);
    if (harden_result < 0) {
        return ENOMEM;
    }
    result = real_posix_spawn(
        pid,
        path,
        file_actions,
        attrp,
        argv,
        harden_result > 0 ? hardened_env : envp
    );
    sb_free_hardened_env(hardened_env, appended_start, appended_count);
    return result;
}

int posix_spawnp(
    pid_t *pid,
    const char *file,
    const posix_spawn_file_actions_t *file_actions,
    const posix_spawnattr_t *attrp,
    char *const argv[],
    char *const envp[]
) {
    static int (*real_posix_spawnp)(pid_t *, const char *, const posix_spawn_file_actions_t *, const posix_spawnattr_t *, char *const[], char *const[]) = NULL;
    char **hardened_env = NULL;
    size_t appended_start = 0;
    size_t appended_count = 0;
    int harden_result;
    int result;
    if (sb_should_block(file, argv)) {
        return EACCES;
    }
    if (real_posix_spawnp == NULL) {
        real_posix_spawnp = dlsym(RTLD_NEXT, "posix_spawnp");
    }
    harden_result = sb_harden_child_env(envp, &hardened_env, &appended_start, &appended_count);
    if (harden_result < 0) {
        return ENOMEM;
    }
    result = real_posix_spawnp(
        pid,
        file,
        file_actions,
        attrp,
        argv,
        harden_result > 0 ? hardened_env : envp
    );
    sb_free_hardened_env(hardened_env, appended_start, appended_count);
    return result;
}
