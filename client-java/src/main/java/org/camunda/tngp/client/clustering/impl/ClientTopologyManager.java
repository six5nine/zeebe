package org.camunda.tngp.client.clustering.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.DirectBuffer;
import org.camunda.tngp.client.clustering.Topology;
import org.camunda.tngp.client.cmd.BrokerRequestException;
import org.camunda.tngp.client.impl.Topic;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.protocol.clientapi.ErrorResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.DeferredCommandContext;
import org.camunda.tngp.util.actor.Actor;
import org.camunda.tngp.util.buffer.BufferReader;


public class ClientTopologyManager implements Actor, BufferReader
{

    protected final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    protected final ErrorResponseDecoder errorResponseDecoder = new ErrorResponseDecoder();

    protected final DeferredCommandContext commandContext = new DeferredCommandContext();

    protected final RequestTopologyCmdImpl requestTopologyCmd;
    protected final ClientTopologyController clientTopologyController;
    protected final List<CompletableFuture<Void>> refreshFutures;

    protected Topology topology;
    protected CompletableFuture<Void> refreshFuture;

    public ClientTopologyManager(final ChannelManager channelManager, final TransportConnectionPool connectionPool, final ObjectMapper objectMapper, final SocketAddress... initialBrokers)
    {
        this.clientTopologyController = new ClientTopologyController(channelManager, connectionPool);
        this.requestTopologyCmd = new RequestTopologyCmdImpl(null, objectMapper);
        this.topology = new TopologyImpl(initialBrokers);

        this.refreshFutures = new ArrayList<>();
        triggerRefresh();
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += commandContext.doWork();
        workCount += clientTopologyController.doWork();

        return workCount;
    }

    public Topology getTopology()
    {
        return topology;
    }

    public SocketAddress getLeaderForTopic(final Topic topic)
    {
        if (topic != null)
        {
            return topology.getLeaderForTopic(topic);
        }
        else
        {
            return topology.getRandomBroker();
        }
    }

    public CompletableFuture<Void> refreshNow()
    {
        return commandContext.runAsync(future -> {
            if (clientTopologyController.isIdle())
            {
                triggerRefresh();
            }

            refreshFutures.add(future);

        });
    }

    protected void triggerRefresh()
    {
        refreshFuture = new CompletableFuture<>();

        refreshFuture.handle((value, throwable) ->
        {
            if (throwable == null)
            {
                refreshFutures.forEach(f -> f.complete(value));
            }
            else
            {
                refreshFutures.forEach(f -> f.completeExceptionally(throwable));
            }

            refreshFutures.clear();

            return null;
        });

        clientTopologyController.configure(topology.getRandomBroker(), requestTopologyCmd.getRequestWriter(), this, refreshFuture);
    }

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length)
    {
        messageHeaderDecoder.wrap(buffer, 0);

        final int schemaId = messageHeaderDecoder.schemaId();
        final int templateId = messageHeaderDecoder.templateId();
        final int blockLength = messageHeaderDecoder.blockLength();
        final int version = messageHeaderDecoder.version();

        final int responseMessageOffset = messageHeaderDecoder.encodedLength();

        final ClientResponseHandler<Topology> responseHandler = requestTopologyCmd.getResponseHandler();

        if (schemaId == responseHandler.getResponseSchemaId() && templateId == responseHandler.getResponseTemplateId())
        {
            try
            {
                topology = responseHandler.readResponse(buffer, responseMessageOffset, blockLength, version);
            }
            catch (final Exception e)
            {
                throw new RuntimeException("Unable to parse topic list from broker response", e);
            }
        }
        else
        {
            errorResponseDecoder.wrap(buffer, offset, blockLength, version);
            throw new BrokerRequestException(errorResponseDecoder.errorCode(), errorResponseDecoder.errorData());
        }

    }

}
