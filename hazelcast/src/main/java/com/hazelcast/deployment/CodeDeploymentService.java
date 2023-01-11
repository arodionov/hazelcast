package com.hazelcast.deployment;

import java.util.Collection;

public interface CodeDeploymentService {

    void deploy(Collection<String> classNames, Collection<String> jarPaths);

    void redeploy(Collection<String> classNames, Collection<String> jarPaths);

    void clear();
}
