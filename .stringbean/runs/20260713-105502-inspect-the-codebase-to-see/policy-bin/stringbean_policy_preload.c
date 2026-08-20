#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <limits.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

extern char **environ;

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

int execve(const char *pathname, char *const argv[], char *const envp[]) {
    static int (*real_execve)(const char *, char *const[], char *const[]) = NULL;
    if (sb_should_block(pathname, argv)) {
        errno = EACCES;
        return -1;
    }
    if (real_execve == NULL) {
        real_execve = dlsym(RTLD_NEXT, "execve");
    }
    return real_execve(pathname, argv, envp);
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
    if (sb_should_block(file, argv)) {
        errno = EACCES;
        return -1;
    }
    if (real_execvpe == NULL) {
        real_execvpe = dlsym(RTLD_NEXT, "execvpe");
    }
    return real_execvpe(file, argv, envp);
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
    if (sb_should_block(path, argv)) {
        return EACCES;
    }
    if (real_posix_spawn == NULL) {
        real_posix_spawn = dlsym(RTLD_NEXT, "posix_spawn");
    }
    return real_posix_spawn(pid, path, file_actions, attrp, argv, envp);
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
    if (sb_should_block(file, argv)) {
        return EACCES;
    }
    if (real_posix_spawnp == NULL) {
        real_posix_spawnp = dlsym(RTLD_NEXT, "posix_spawnp");
    }
    return real_posix_spawnp(pid, file, file_actions, attrp, argv, envp);
}
