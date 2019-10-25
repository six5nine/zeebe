package io.zeebe.logstreams.impl.storage.atomix;

import io.atomix.protocols.raft.storage.log.RaftLogReader;
import io.atomix.protocols.raft.zeebe.ZeebeEntry;
import io.atomix.storage.journal.Indexed;
import java.util.Optional;

public class AtomixLogReader {
  // naive optimization would be to pool the readers we create
  private final AtomixReaderFactory factory;

  // use a fixed reader strictly for first/last index isn't create, but works for now
  private final RaftLogReader indexReader;
  private final RaftLogReader reader;
  private boolean closed;

  AtomixLogReader(final AtomixReaderFactory factory) {
    this.factory = factory;
    this.indexReader = factory.create();
    this.reader = factory.create();
  }

  long getFirstIndex() {
    return indexReader.getFirstIndex();
  }

  long getLastIndex() {
    return indexReader.getLastIndex();
  }

  /**
   * Looks up the entry whose index is either the given index, or the closest lower index.
   *
   * @param index index to seek to
   */
  Optional<Indexed<ZeebeEntry>> read(final long index) {
    try (final var reader = getReader(index)) {
      while (reader.hasNext()) {
        final var entry = reader.next();
        if (entry.type().equals(ZeebeEntry.class)) {
          return Optional.of(entry.cast());
        }
      }
    }

    return Optional.empty();
  }

  public void close() {
    indexReader.close();
    reader.close();
    closed = true;
  }

  private RaftLogReader getReader(final long index) {
    // return factory.create(index);
    reader.reset(index);
    return reader;
  }
}
