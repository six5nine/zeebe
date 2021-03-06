/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_ADVERTISED_HOST;
import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_HOST;
import static io.zeebe.broker.system.configuration.EnvironmentConstants.ENV_PORT_OFFSET;

import io.zeebe.broker.system.configuration.SocketBindingCfg.CommandApiCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg.MonitoringApiCfg;
import io.zeebe.util.ByteValue;
import io.zeebe.util.Environment;
import java.util.Optional;

public final class NetworkCfg implements ConfigurationEntry {

  public static final String DEFAULT_HOST = "0.0.0.0";
  public static final int DEFAULT_COMMAND_API_PORT = 26501;
  public static final int DEFAULT_MONITORING_API_PORT = 9600;
  public static final String DEFAULT_MAX_MESSAGE_SIZE = "4M";

  private String host = DEFAULT_HOST;
  private int portOffset = 0;
  private String maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
  private String advertisedHost;

  private final CommandApiCfg commandApi = new CommandApiCfg();
  private MonitoringApiCfg monitoringApi = new MonitoringApiCfg();

  @Override
  public void init(
      final BrokerCfg brokerCfg, final String brokerBase, final Environment environment) {
    applyEnvironment(environment);
    commandApi.applyDefaults(this);
    monitoringApi.applyDefaults(this);
  }

  private void applyEnvironment(final Environment environment) {
    environment.get(ENV_HOST).ifPresent(this::setHost);
    environment.getInt(ENV_PORT_OFFSET).ifPresent(this::setPortOffset);
    environment.get(ENV_ADVERTISED_HOST).ifPresent(this::setAdvertisedHost);
  }

  public String getHost() {
    return host;
  }

  public void setHost(final String host) {
    this.host = host;
  }

  public void setAdvertisedHost(final String advertisedHost) {
    this.advertisedHost = advertisedHost;
  }

  public String getAdvertisedHost() {
    return Optional.ofNullable(advertisedHost).orElse(getHost());
  }

  public int getPortOffset() {
    return portOffset;
  }

  public void setPortOffset(final int portOffset) {
    this.portOffset = portOffset;
  }

  public ByteValue getMaxMessageSize() {
    return new ByteValue(maxMessageSize);
  }

  public void setMaxMessageSize(final String maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
  }

  public CommandApiCfg getCommandApi() {
    return commandApi;
  }

  public SocketBindingCfg getMonitoringApi() {
    return monitoringApi;
  }

  public void setMonitoringApi(final MonitoringApiCfg monitoringApi) {
    this.monitoringApi = monitoringApi;
  }

  @Override
  public String toString() {
    return "NetworkCfg{"
        + "host='"
        + host
        + '\''
        + ", portOffset="
        + portOffset
        + '\''
        + ", maxMessageSize="
        + maxMessageSize
        + ", commandApi="
        + commandApi
        + ", monitoringApi="
        + monitoringApi
        + '}';
  }
}
