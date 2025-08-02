package com.scanales.eventflow.config;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.jgit.lib.CoreConfig;

/**
 * Ensures JGit enums used during native image build are available via reflection.
 */
@RegisterForReflection(targets = {
        CoreConfig.TrustLooseRefStat.class,
        CoreConfig.TrustPackedRefsStat.class,
        CoreConfig.TrustStat.class,
        CoreConfig.HideDotFiles.class
})
public final class JGitReflectConfig {
    static {
        // Touch the enums so GraalVM keeps the values() methods
        CoreConfig.TrustLooseRefStat.values();
        CoreConfig.TrustPackedRefsStat.values();
        CoreConfig.TrustStat.values();
        CoreConfig.HideDotFiles.values();
    }
    // class only used for reflection registration
}
