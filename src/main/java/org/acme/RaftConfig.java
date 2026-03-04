package org.acme;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "raft")
public interface RaftConfig {

    String nodeId();

    int port();

    String storageDir();

    String peers();
}
