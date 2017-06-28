/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.camunda.tngp.client.task.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;
import static org.camunda.tngp.util.VarDataUtil.readBytes;
import static org.mockito.Mockito.mock;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.client.impl.ClientCommandManager;
import org.camunda.tngp.client.impl.Topic;
import org.camunda.tngp.client.impl.cmd.ClientResponseHandler;
import org.camunda.tngp.client.task.impl.CreateTaskSubscriptionCmdImpl;
import org.camunda.tngp.client.task.impl.TaskSubscription;
import org.camunda.tngp.client.task.impl.subscription.EventSubscriptionCreationResult;
import org.camunda.tngp.protocol.clientapi.ControlMessageRequestDecoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageResponseEncoder;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class CreateTaskSubscriptionCmdTest
{
    private static final byte[] BUFFER = new byte[1014 * 1024];

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final ControlMessageRequestDecoder requestDecoder = new ControlMessageRequestDecoder();
    private final ControlMessageResponseEncoder responseEncoder = new ControlMessageResponseEncoder();

    private final UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);

    private CreateTaskSubscriptionCmdImpl command;
    private ObjectMapper objectMapper;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setup()
    {
        final ClientCommandManager commandManager = mock(ClientCommandManager.class);

        objectMapper = new ObjectMapper(new MessagePackFactory());

        command = new CreateTaskSubscriptionCmdImpl(commandManager, objectMapper, new Topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID));

        writeBuffer.wrap(BUFFER);
    }

    @Test
    public void shouldWriteRequest() throws JsonParseException, JsonMappingException, IOException
    {
        // given
        command
            .taskType("foo")
            .lockDuration(1000)
            .lockOwner("owner")
            .initialCredits(5);

        // when
        command.getRequestWriter().write(writeBuffer, 0);

        // then
        headerDecoder.wrap(writeBuffer, 0);

        assertThat(headerDecoder.schemaId()).isEqualTo(requestDecoder.sbeSchemaId());
        assertThat(headerDecoder.version()).isEqualTo(requestDecoder.sbeSchemaVersion());
        assertThat(headerDecoder.templateId()).isEqualTo(requestDecoder.sbeTemplateId());
        assertThat(headerDecoder.blockLength()).isEqualTo(requestDecoder.sbeBlockLength());

        requestDecoder.wrap(writeBuffer, headerDecoder.encodedLength(), requestDecoder.sbeBlockLength(), requestDecoder.sbeSchemaVersion());

        assertThat(requestDecoder.messageType()).isEqualTo(ControlMessageType.ADD_TASK_SUBSCRIPTION);

        final byte[] data = readBytes(requestDecoder::getData, requestDecoder::dataLength);

        final TaskSubscription taskSubscription = objectMapper.readValue(data, TaskSubscription.class);

        assertThat(taskSubscription.getTopicName()).isEqualTo(DEFAULT_TOPIC_NAME);
        assertThat(taskSubscription.getPartitionId()).isEqualTo(DEFAULT_PARTITION_ID);
        assertThat(taskSubscription.getTaskType()).isEqualTo("foo");
        assertThat(taskSubscription.getLockDuration()).isEqualTo(1000);
        assertThat(taskSubscription.getLockOwner()).isEqualTo("owner");
        assertThat(taskSubscription.getCredits()).isEqualTo(5);
    }

    @Test
    public void shouldReadResponse() throws JsonProcessingException
    {
        final ClientResponseHandler<EventSubscriptionCreationResult> responseHandler = command.getResponseHandler();

        assertThat(responseHandler.getResponseSchemaId()).isEqualTo(responseEncoder.sbeSchemaId());
        assertThat(responseHandler.getResponseTemplateId()).isEqualTo(responseEncoder.sbeTemplateId());

        responseEncoder.wrap(writeBuffer, 0);

        // given
        final TaskSubscription taskSubscription = new TaskSubscription();
        taskSubscription.setSubscriberKey(3L);

        final byte[] jsonData = objectMapper.writeValueAsBytes(taskSubscription);

        responseEncoder.putData(jsonData, 0, jsonData.length);

        // when
        final EventSubscriptionCreationResult result = responseHandler.readResponse(writeBuffer, 0, responseEncoder.sbeBlockLength(), responseEncoder.sbeSchemaVersion());

        // then
        assertThat(result.getSubscriberKey()).isEqualTo(3L);
    }

    @Test
    public void shouldBeNotValidIfTaskTypeIsNotSet()
    {
        command
            .lockDuration(1000)
            .lockOwner("owner")
            .initialCredits(5);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("task type must not be null");

        command.validate();
    }

    @Test
    public void shouldBeNotValidIfLockDurationIsNotSet()
    {
        command
            .taskType("foo")
            .lockOwner("owner")
            .initialCredits(5);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("lock duration must be greater than 0");

        command.validate();
    }

    @Test
    public void shouldBeNotValidIfLockOwnerIsNotSet()
    {
        command
            .taskType("foo")
            .lockDuration(1000)
            .initialCredits(5);

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("lock owner must not be null");

        command.validate();
    }

    @Test
    public void shouldBeNotValidIfInitialCreditsAreNotSet()
    {
        command
            .taskType("foo")
            .lockDuration(1000)
            .lockOwner("owner");

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("initial credits must be greater than 0");

        command.validate();
    }

}
