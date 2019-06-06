/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map.impl.operation;

import com.hazelcast.map.EntryProcessor;
import com.hazelcast.query.Predicate;

/**
 * Provides common backup operation functionality for {@link com.hazelcast.map.EntryProcessor}
 * that can run on multiple entries.
 */
abstract class AbstractMultipleEntryBackupOperation extends MapOperation {

    EntryProcessor backupProcessor;

    AbstractMultipleEntryBackupOperation() {
    }

    AbstractMultipleEntryBackupOperation(String name, EntryProcessor backupProcessor) {
        super(name);
        this.backupProcessor = backupProcessor;
    }

    protected Predicate getPredicate() {
        return null;
    }

}
