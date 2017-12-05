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
package io.zeebe.gossip.membership;

import java.util.*;

import io.zeebe.gossip.*;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.time.ClockUtil;

public class MembershipList implements Iterable<Member>
{
    private final Member self;
    private final GossipConfiguration configuration;

    private final List<Member> members = new ArrayList<>();

    private final List<GossipMembershipListener> listeners = new ArrayList<>();

    private final MembershipIterator iterator = new MembershipIterator();

    public MembershipList(SocketAddress address, GossipConfiguration configuration)
    {
        this.self = new Member(address);
        this.configuration = configuration;
    }

    public Member self()
    {
        return self;
    }

    public boolean hasMember(String id)
    {
        return get(id) != null;
    }

    public Member newMember(String id, GossipTerm term)
    {
        final Member member = new Member(SocketAddress.from(id));
        member.getTerm().epoch(term.getEpoch()).heartbeat(term.getHeartbeat());

        members.add(member);

        listeners.forEach(c -> c.onAdd(member));

        return member;
    }

    public void removeMember(String id)
    {
        final Member member = get(id);
        if (member != null)
        {
            members.remove(member);

            listeners.forEach(c -> c.onRemove(member));
        }
    }

    public void aliveMember(String id, GossipTerm gossipTerm)
    {
        final Member member = get(id);
        if (member != null)
        {
            member
                .setStatus(MembershipStatus.ALIVE)
                .setGossipTerm(gossipTerm)
                .setSuspictionTimeout(-1L);
        }
    }

    public void suspectMember(String id, GossipTerm gossipTerm)
    {
        final Member member = get(id);
        if (member != null)
        {
            member
                .setStatus(MembershipStatus.SUSPECT)
                .setGossipTerm(gossipTerm)
                .setSuspictionTimeout(calculateSuspictionTimeout());
        }
    }

    private long calculateSuspictionTimeout()
    {
        final int multiplier = configuration.getSuspicionMultiplier();
        final int clusterSize = 1 + size();
        final int probeInterval = configuration.getProbeInterval();

        final long timeout = GossipMathUtil.suspicionTimeout(multiplier, clusterSize, probeInterval);

        return ClockUtil.getCurrentTimeInMillis() + timeout;
    }

    public Member get(String id)
    {
        for (int m = 0; m < members.size(); m++)
        {
            final Member member = members.get(m);

            if (member.getId().equals(id))
            {
                return member;
            }
        }
        return null;
    }

    public int size()
    {
        return members.size();
    }

    public List<Member> getMembersView()
    {
        return members;
    }

    public void addListener(GossipMembershipListener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(GossipMembershipListener listener)
    {
        listeners.remove(listener);
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("MembershipList [self=");
        builder.append(self);
        builder.append(", members=");
        builder.append(members);
        builder.append("]");
        return builder.toString();
    }


    @Override
    public Iterator<Member> iterator()
    {
        iterator.reset();
        return iterator;
    }

    private class MembershipIterator implements Iterator<Member>
    {
        private int index = 0;

        public void reset()
        {
            index = 0;
        }

        @Override
        public boolean hasNext()
        {
            return index < members.size();
        }

        @Override
        public Member next()
        {
            if (hasNext())
            {
                final Member member = members.get(index);

                index += 1;

                return member;
            }
            else
            {
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove()
        {
            index -= 1;

            final Member member = members.get(index);
            removeMember(member.getId());
        }

    }

}
