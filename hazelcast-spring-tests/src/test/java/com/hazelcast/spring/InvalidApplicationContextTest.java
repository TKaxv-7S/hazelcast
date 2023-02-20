/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.spring;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class InvalidApplicationContextTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void shouldFailWhenLoadBalancerContainsClassNameAndImplementationAttribute() {
        expectedException.expect(BeanDefinitionStoreException.class);
        expectedException.expectMessage("Unexpected exception parsing XML document from class path resource [com/hazelcast/spring/"
                + "customLoadBalancer-invalidApplicationContext.xml]; nested exception is "
                + "java.lang.IllegalArgumentException: Exactly one of 'class-name' or 'implementation' attributes is "
                + "required to create LoadBalancer!");

        new ClassPathXmlApplicationContext("com\\hazelcast\\spring\\customLoadBalancer-invalidApplicationContext.xml");
    }
}
