package com.chuansuo.app;

/** Canonical links for the GitHub repository configured for this build. */
public final class ProjectIdentity {
    private static final String REPOSITORY = BuildConfig.GITHUB_REPOSITORY.trim();

    private ProjectIdentity() {}

    public static String homeUrl() {
        return repositoryUrl("https://github.com/", "");
    }

    public static String issuesUrl() {
        return repositoryUrl("https://github.com/", "/issues");
    }

    public static String latestReleaseApiUrl() {
        return repositoryUrl("https://api.github.com/repos/", "/releases/latest");
    }

    private static String repositoryUrl(String base, String suffix) {
        return REPOSITORY.isEmpty() ? "" : base + REPOSITORY + suffix;
    }
}
