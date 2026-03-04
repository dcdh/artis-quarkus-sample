package org.acme;

import io.quarkus.logging.Log;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.proto.RaftProtos;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;
import org.apache.ratis.protocol.RaftPeerId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.impl.BaseStateMachine;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@QuarkusMain
public class ApplicationEntrypoint implements QuarkusApplication {

    @Inject
    RaftConfig config;

    @Override
    public int run(String... args) throws Exception {
        List<RaftPeer> peers = buildPeers();
        RaftGroupId groupId = RaftGroupId.valueOf(new UUID(0, 1));
        RaftGroup group = RaftGroup.valueOf(groupId, peers);

        RaftProperties properties = new RaftProperties();
        GrpcConfigKeys.Server.setPort(properties, config.port());
        RaftServerConfigKeys.setStorageDir(
                properties,
                List.of(new File(config.storageDir())));

        try (RaftServer server = RaftServer.newBuilder()
                .setGroup(group)
                .setProperties(properties)
                .setServerId(RaftPeerId.valueOf(config.nodeId()))
                .setStateMachine(new BaseStateMachine())
                .setOption(RaftStorage.StartupOption.RECOVER)
                .build()) {

            server.start();

            Log.info("Node started: " + config.nodeId());

            try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()) {
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        RaftServer.Division division = server.getDivision(groupId);
                        RaftProtos.RaftPeerRole role = division.getInfo().getCurrentRole();

                        if (role == RaftProtos.RaftPeerRole.LEADER) {
                            Log.info("I am the leader - Start Debezium task now if not started yet !");
                        } else {
                            Log.info("I am not the leader");
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, 0, 10, TimeUnit.SECONDS);
                Thread.currentThread().join();
            }
        }
        return 0;
    }

    public List<RaftPeer> buildPeers() {
        return Arrays.stream(config.peers().split(","))
                .map(String::trim)
                .map(entry -> {
                    String[] parts = entry.split("@");
                    String id = parts[0];
                    String address = parts[1];

                    return RaftPeer.newBuilder()
                            .setId(id)
                            .setAddress(address)
                            .build();
                })
                .toList();
    }
}
