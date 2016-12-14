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
package org.camunda.tngp.logstreams.integration.util;

import java.io.File;

import org.camunda.tngp.logstreams.fs.FsLogStreamBuilder;
import org.camunda.tngp.logstreams.impl.log.fs.FsLogStorage;
import org.camunda.tngp.logstreams.impl.log.fs.FsLogStorageConfiguration;
import org.camunda.tngp.logstreams.log.StreamContext;

public class ControllableFsLogStreamBuilder extends FsLogStreamBuilder
{

    public ControllableFsLogStreamBuilder(String name, int id)
    {
        super(name, id);
    }

    @Override
    protected void initLogStorage(StreamContext ctx)
    {
        if (logDirectory == null)
        {
            logDirectory = logRootPath + File.separatorChar + name + File.separatorChar;
        }

        final File file = new File(logDirectory);
        file.mkdirs();

        final FsLogStorageConfiguration storageConfig = new FsLogStorageConfiguration(logSegmentSize,
                logDirectory,
                initialLogSegmentId,
                deleteOnClose);

        final FsLogStorage storage = new ControllableFsLogStorage(storageConfig);

        storage.open();

        ctx.setLogStorage(storage);
    }

}
