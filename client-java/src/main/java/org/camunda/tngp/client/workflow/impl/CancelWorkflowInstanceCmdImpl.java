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
package org.camunda.tngp.client.workflow.impl;

import static org.camunda.tngp.util.EnsureUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.cmd.ClientCommandRejectedException;
import org.camunda.tngp.client.impl.ClientCommandManager;
import org.camunda.tngp.client.impl.Topic;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.workflow.cmd.CancelWorkflowInstanceCmd;
import org.camunda.tngp.protocol.clientapi.EventType;

public class CancelWorkflowInstanceCmdImpl extends AbstractExecuteCmdImpl<WorkflowInstanceEvent, Void> implements CancelWorkflowInstanceCmd
{
    private static final String ERROR_MESSAGE = "Failed to cancel workflow instance with key '%d'.";

    private final WorkflowInstanceEvent workflowInstanceEvent = new WorkflowInstanceEvent();

    private long workflowInstanceKey;

    public CancelWorkflowInstanceCmdImpl(final ClientCommandManager commandManager, final ObjectMapper objectMapper, final Topic topic)
    {
        super(commandManager, objectMapper, topic, WorkflowInstanceEvent.class, EventType.WORKFLOW_EVENT);
    }

    @Override
    public CancelWorkflowInstanceCmd workflowInstanceKey(long key)
    {
        this.workflowInstanceKey = key;
        return this;
    }

    @Override
    public void validate()
    {
        super.validate();

        ensureGreaterThan("workflow instance key", workflowInstanceKey, 0);
    }

    @Override
    protected Object writeCommand()
    {
        workflowInstanceEvent.setEventType(WorkflowInstanceEventType.CANCEL_WORKFLOW_INSTANCE);

        return workflowInstanceEvent;
    }

    @Override
    protected long getKey()
    {
        return workflowInstanceKey;
    }

    @Override
    protected void reset()
    {
        workflowInstanceKey = -1L;
    }

    @Override
    protected Void getResponseValue(long key, WorkflowInstanceEvent event)
    {
        if (event.getEventType() == WorkflowInstanceEventType.CANCEL_WORKFLOW_INSTANCE_REJECTED)
        {
            throw new ClientCommandRejectedException(String.format(ERROR_MESSAGE, key));
        }

        return null;
    }

}
