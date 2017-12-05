/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gossip.failuredetection;

import java.util.*;

import io.zeebe.clustering.gossip.MembershipEventType;
import io.zeebe.gossip.GossipConfiguration;
import io.zeebe.gossip.GossipContext;
import io.zeebe.gossip.dissemination.DisseminationComponent;
import io.zeebe.gossip.membership.Member;
import io.zeebe.gossip.protocol.*;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.actor.Actor;
import io.zeebe.util.state.*;
import io.zeebe.util.time.ClockUtil;
import org.slf4j.Logger;

public class JoinController implements Actor
{
    private static final int TRANSITION_DEFAULT = 0;
    private static final int TRANSITION_RECEIVED = 1;
    private static final int TRANSITION_TIMEOUT = 2;
    private static final int TRANSITION_FAIL = 3;
    private static final int TRANSITION_JOIN = 4;

    private final Logger logger;
    private final GossipConfiguration config;

    private final StateMachine<Context> stateMachine;

    public JoinController(GossipContext context)
    {
        this.logger = context.getLogger();
        this.config = context.getConfiguration();

        final WaitState<Context> awaitJoinState = ctx ->
        { };

        final WaitState<Context> joinedState = ctx ->
        { };

        final SendJoinState sendJoinState = new SendJoinState(context.getDisseminationComponent(), context.getMemberList().self(), context.getGossipEventSender());
        final AwaitJoinResponseState awaitJoinResponseState = new AwaitJoinResponseState(context.getGossipEventFactory());
        final SendSyncRequestState sendSyncRequestState = new SendSyncRequestState(context.getGossipEventSender());
        final AwaitSyncResponseState awaitSyncResponseState = new AwaitSyncResponseState();
        final AwaitNextJoinIntervalState awaitRetryState = new AwaitNextJoinIntervalState();

        this.stateMachine = StateMachine.<Context> builder(sm -> new Context(sm, context.getGossipEventFactory()))
                .initialState(awaitJoinState)
                .from(awaitJoinState).take(TRANSITION_JOIN).to(sendJoinState)
                .from(sendJoinState).take(TRANSITION_DEFAULT).to(awaitJoinResponseState)
                .from(awaitJoinResponseState).take(TRANSITION_RECEIVED).to(sendSyncRequestState)
                .from(awaitJoinResponseState).take(TRANSITION_TIMEOUT).to(awaitRetryState)
                .from(sendSyncRequestState).take(TRANSITION_DEFAULT).to(awaitSyncResponseState)
                .from(awaitSyncResponseState).take(TRANSITION_DEFAULT).to(joinedState)
                .from(awaitSyncResponseState).take(TRANSITION_FAIL).to(awaitRetryState)
                .from(awaitSyncResponseState).take(TRANSITION_TIMEOUT).to(awaitRetryState)
                .from(awaitRetryState).take(TRANSITION_DEFAULT).to(sendJoinState)
                .from(awaitRetryState).take(TRANSITION_JOIN).to(sendJoinState)
                .build();
    }

    @Override
    public int doWork() throws Exception
    {
        return stateMachine.doWork();
    }

    public void join(List<SocketAddress> contactPoints)
    {
        final boolean success = stateMachine.tryTake(TRANSITION_JOIN);
        if (success)
        {
            logger.debug("Join cluster with known contact points: {}", contactPoints);

            final Context context = stateMachine.getContext();
            context.contactPoints = new ArrayList<>(contactPoints);
            context.requests = new ArrayList<>(contactPoints.size());
        }
    }

    private class Context extends SimpleStateMachineContext
    {
        private final GossipEventResponse syncResponse;

        private List<SocketAddress> contactPoints;
        private List<ClientRequest> requests;
        private long joinTimeout;
        private long nextJoinInterval;
        private SocketAddress contactPoint;

        Context(StateMachine<Context> stateMachine, GossipEventFactory eventFactory)
        {
            super(stateMachine);
            this.syncResponse = new GossipEventResponse(eventFactory.createSyncEvent());
            clear();
        }

        private void clear()
        {
            contactPoints = Collections.emptyList();
            requests = Collections.emptyList();
            contactPoint = null;
        }
    }

    private class SendJoinState implements TransitionState<Context>
    {
        private final DisseminationComponent disseminationComponent;
        private final Member self;
        private final GossipEventSender gossipEventSender;

        SendJoinState(DisseminationComponent disseminationComponent, Member self, GossipEventSender gossipEventSender)
        {
            this.disseminationComponent = disseminationComponent;
            this.self = self;
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            disseminationComponent.addMembershipEvent()
                .memberId(self.getId())
                .type(MembershipEventType.JOIN)
                .gossipTerm(self.getTerm());

            for (SocketAddress contactPoint : context.contactPoints)
            {
                if (!self.getAddress().equals(contactPoint))
                {
                    logger.trace("Spread JOIN event to contact point '{}'", contactPoint);

                    final ClientRequest request = gossipEventSender.sendPing(contactPoint);
                    context.requests.add(request);
                }
            }

            context.joinTimeout = ClockUtil.getCurrentTimeInMillis() + config.getJoinTimeout();
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitJoinResponseState implements WaitState<Context>
    {
        private final GossipEventResponse response;

        AwaitJoinResponseState(GossipEventFactory eventFactory)
        {
            this.response = new GossipEventResponse(eventFactory.createFailureDetectionEvent());
        }

        @Override
        public void work(Context context) throws Exception
        {
            final long currentTime = ClockUtil.getCurrentTimeInMillis();

            // only wait for the first response
            SocketAddress contactPoint = null;

            for (int r = 0; r < context.requests.size() && contactPoint == null; r++)
            {
                response.wrap(context.requests.get(r));

                if (response.isReceived())
                {
                    contactPoint = context.contactPoints.get(r);
                    logger.trace("Received response from contact point '{}'", contactPoint);

                    response.process();
                }
            }

            if (contactPoint != null)
            {
                context.contactPoint = contactPoint;
                context.take(TRANSITION_RECEIVED);
            }
            else if (currentTime >= context.joinTimeout)
            {
                logger.warn("Failed to contact any of '{}'. Try again in {}ms", context.contactPoints, config.getJoinInterval());

                context.nextJoinInterval = currentTime + config.getJoinInterval();
                context.take(TRANSITION_TIMEOUT);
            }
        }

        @Override
        public void onExit()
        {
            response.clear();
            stateMachine.getContext().requests.clear();
        }
    }

    private class SendSyncRequestState implements TransitionState<Context>
    {
        private final GossipEventSender gossipEventSender;

        SendSyncRequestState(GossipEventSender gossipEventSender)
        {
            this.gossipEventSender = gossipEventSender;
        }

        @Override
        public void work(Context context) throws Exception
        {
            logger.trace("Send SYNC request to '{}'", context.contactPoint);

            final ClientRequest request = gossipEventSender.sendSyncRequest(context.contactPoint);

            context.syncResponse.wrap(request, config.getSyncTimeout());
            context.take(TRANSITION_DEFAULT);
        }
    }

    private class AwaitSyncResponseState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            final GossipEventResponse response = context.syncResponse;
            if (response.isReceived())
            {
                logger.trace("Received SYNC response from '{}'", context.contactPoint);

                response.process();

                context.clear();
                context.take(TRANSITION_DEFAULT);
            }
            else if (response.isFailed())
            {
                logger.trace("Failed to receive SYNC response from '{}'", context.contactPoint);

                context.take(TRANSITION_FAIL);
            }
            else if (response.isTimedOut())
            {
                logger.warn("Doesn't receive SYNC response from '{}'. Try again in {}ms", context.contactPoint, config.getJoinInterval());

                context.nextJoinInterval = ClockUtil.getCurrentTimeInMillis() + config.getJoinInterval();
                context.take(TRANSITION_TIMEOUT);
            }
        }

        @Override
        public void onExit()
        {
            stateMachine.getContext().syncResponse.clear();
        }
    }

    private class AwaitNextJoinIntervalState implements WaitState<Context>
    {
        @Override
        public void work(Context context) throws Exception
        {
            if (ClockUtil.getCurrentTimeInMillis() >= context.nextJoinInterval)
            {
                context.take(TRANSITION_DEFAULT);
            }
        }
    }

}
