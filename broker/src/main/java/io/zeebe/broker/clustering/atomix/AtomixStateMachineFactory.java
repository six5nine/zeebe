package io.zeebe.broker.clustering.atomix;

import io.atomix.protocols.raft.RaftStateMachine;
import io.atomix.protocols.raft.RaftStateMachineFactory;
import io.atomix.protocols.raft.impl.RaftContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.atomix.utils.concurrent.ThreadContextFactory;
import java.util.concurrent.ConcurrentHashMap;

public class AtomixStateMachineFactory
    implements RaftStateMachineFactory, AtomixPositionBroadcaster {
  private final ConcurrentHashMap<String, AtomixPositionListener> listeners;

  public AtomixStateMachineFactory(final int count) {
    this.listeners = new ConcurrentHashMap<>(count);
  }

  @Override
  public RaftStateMachine createStateMachine(
      final RaftContext raft,
      final ThreadContext stateContext,
      final ThreadContextFactory threadContextFactory) {
    return new AtomixStateMachine(raft, stateContext, threadContextFactory, this);
  }

  @Override
  public void setPositionListener(final String raftName, final AtomixPositionListener listener) {
    listeners.put(raftName, listener);
  }

  @Override
  public void removePositionListener(final String raftName) {
    listeners.remove(raftName);
  }

  @Override
  public void removeAllPositionListeners() {
    listeners.clear();
  }

  @Override
  public void notifyPositionListener(final String raftName, final long position) {
    final var listener = listeners.get(raftName);
    if (listener != null) {
      listener.acceptPosition(position);
    }
  }
}
