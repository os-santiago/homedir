package com.scanales.eventflow.config;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;

public class JGitNativeImageBuildStep {

    @BuildStep
    void enableRuntimeInit(BuildProducer<RuntimeInitializedClassBuildItem> init) {
        init.produce(new RuntimeInitializedClassBuildItem("org.eclipse.jgit.lib.internal.WorkQueue"));
        init.produce(new RuntimeInitializedClassBuildItem("org.eclipse.jgit.internal.storage.file.WindowCache"));
        init.produce(new RuntimeInitializedClassBuildItem("org.eclipse.jgit.util.FileUtils"));
    }
}
