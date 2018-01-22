/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.lettuce.core.masterslave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.models.role.RedisInstance;
import io.lettuce.core.models.role.RedisNodeDescription;
import io.lettuce.core.protocol.RedisCommand;

/**
 * @author Mark Paluch
 */
@RunWith(MockitoJUnitRunner.class)
public class MasterSlaveTopologyRefreshTest {

    private static final RedisMasterSlaveNode MASTER = new RedisMasterSlaveNode("localhost", 1, new RedisURI(),
            RedisInstance.Role.MASTER);

    private static final RedisMasterSlaveNode SLAVE = new RedisMasterSlaveNode("localhost", 2, new RedisURI(),
            RedisInstance.Role.SLAVE);

    @Mock
    NodeConnectionFactory connectionFactory;

    @Mock
    StatefulRedisConnection<String, String> connection;

    @Mock
    RedisAsyncCommands<String, String> async;

    TopologyProvider provider;

    @Before
    public void before() {

        when(connection.async()).thenReturn(async);
        when(connection.dispatch(any(RedisCommand.class))).then(invocation -> {

            RedisCommand command = invocation.getArgument(0);
            command.complete();

            return null;
        });

        provider = () -> Arrays.asList(MASTER, SLAVE);
    }

    @Test
    public void shouldRetrieveTopology() {

        MasterSlaveTopologyRefresh refresh = new MasterSlaveTopologyRefresh(connectionFactory, provider);

        CompletableFuture<StatefulRedisConnection<String, String>> master = CompletableFuture.completedFuture(connection);
        CompletableFuture<StatefulRedisConnection<String, String>> slave = CompletableFuture.completedFuture(connection);
        when(connectionFactory.connectToNodeAsync(any(), any())).thenReturn((CompletableFuture) master,
                (CompletableFuture) slave);

        RedisURI redisURI = new RedisURI();
        redisURI.setTimeout(Duration.ofMillis(1));

        List<RedisNodeDescription> nodes = refresh.getNodes(redisURI);

        assertThat(nodes).hasSize(2);
    }

    @Test
    public void shouldRetrieveTopologyWithFailedNode() {

        MasterSlaveTopologyRefresh refresh = new MasterSlaveTopologyRefresh(connectionFactory, provider);

        CompletableFuture<StatefulRedisConnection<String, String>> connected = CompletableFuture.completedFuture(connection);
        CompletableFuture<StatefulRedisConnection<String, String>> pending = new CompletableFuture<>();
        when(connectionFactory.connectToNodeAsync(any(), any())).thenReturn((CompletableFuture) connected,
                (CompletableFuture) pending);

        RedisURI redisURI = new RedisURI();
        redisURI.setTimeout(Duration.ofMillis(1));

        List<RedisNodeDescription> nodes = refresh.getNodes(redisURI);

        assertThat(nodes).hasSize(1).containsOnly(MASTER);
    }
}