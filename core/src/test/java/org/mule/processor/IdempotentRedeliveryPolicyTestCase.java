/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.processor;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleProperties;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.exception.MessageRedeliveredException;
import org.mule.api.processor.MessageProcessor;
import org.mule.api.store.ObjectStore;
import org.mule.api.store.ObjectStoreException;
import org.mule.api.store.ObjectStoreManager;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.util.SerializationUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IdempotentRedeliveryPolicyTestCase extends AbstractMuleTestCase
{

    public static final String STRING_MESSAGE = "message";
    private MuleContext mockMuleContext = mock(MuleContext.class, Answers.RETURNS_DEEP_STUBS.get());
    private ObjectStoreManager mockObjectStoreManager = mock(ObjectStoreManager.class, Answers.RETURNS_DEEP_STUBS.get());
    private MessageProcessor mockFailingMessageProcessor = mock(MessageProcessor.class, Answers.RETURNS_DEEP_STUBS.get());
    private MuleMessage message = mock(MuleMessage.class, Answers.RETURNS_DEEP_STUBS.get());
    private MuleEvent event = mock(MuleEvent.class, Answers.RETURNS_DEEP_STUBS.get());

    @Before
    public void setUpTest() throws MuleException
    {
        when(mockFailingMessageProcessor.process(any(MuleEvent.class))).thenThrow(new RuntimeException("failing"));
        System.setProperty(MuleProperties.MULE_ENCODING_SYSTEM_PROPERTY,"utf-8");
    }

    @Test
    public void messageDigestFailure() throws Exception
    {
        Mockito.when(mockMuleContext.getRegistry().get(MuleProperties.OBJECT_STORE_MANAGER)).thenReturn(mockObjectStoreManager);
        Mockito.when(mockObjectStoreManager.getObjectStore(anyString(), anyBoolean(), anyInt(), anyInt(), anyInt())).thenReturn(new InMemoryObjectStore());

        IdempotentRedeliveryPolicy irp = new IdempotentRedeliveryPolicy();
        irp.setUseSecureHash(true);
        irp.setMaxRedeliveryCount(1);
        irp.setFlowConstruct(mock(FlowConstruct.class));
        irp.setMuleContext(mockMuleContext);
        irp.initialise();


        when(message.getPayload()).thenReturn(new Object());

        when(event.getMessage()).thenReturn(message);
        MuleEvent process = irp.process(event);
        Assert.assertNull(process);
    }

    @Test(expected = MessageRedeliveredException.class)
    public void testMessageRedeliveryUsingMemory() throws Exception
    {
        Mockito.when(mockMuleContext.getRegistry().get(MuleProperties.OBJECT_STORE_MANAGER)).thenReturn(mockObjectStoreManager);
        Mockito.when(mockObjectStoreManager.getObjectStore(anyString(),anyBoolean(),anyInt(),anyInt(),anyInt())).thenReturn(new InMemoryObjectStore());

        IdempotentRedeliveryPolicy irp = new IdempotentRedeliveryPolicy();
        irp.setMaxRedeliveryCount(0);
        irp.setUseSecureHash(true);
        irp.setFlowConstruct(mock(FlowConstruct.class));
        irp.setMuleContext(mockMuleContext);
        irp.setListener(mockFailingMessageProcessor);
        irp.initialise();

        when(message.getPayload()).thenReturn(STRING_MESSAGE);
        when(event.getMessage()).thenReturn(message);

        try
        {
            irp.process(event);
        }
        catch (Exception e)
        {
        }
        irp.process(event);
    }

    @Test(expected = MessageRedeliveredException.class)
    public void testMessageRedeliveryUsingSerializationStore() throws Exception
    {
        Mockito.when(mockMuleContext.getRegistry().get(MuleProperties.OBJECT_STORE_MANAGER)).thenReturn(mockObjectStoreManager);
        Mockito.when(mockObjectStoreManager.getObjectStore(anyString(),anyBoolean(),anyInt(),anyInt(),anyInt())).thenReturn(new SerializationObjectStore());

        IdempotentRedeliveryPolicy irp = new IdempotentRedeliveryPolicy();
        irp.setUseSecureHash(true);
        irp.setMaxRedeliveryCount(0);
        irp.setFlowConstruct(mock(FlowConstruct.class));
        irp.setMuleContext(mockMuleContext);
        irp.setListener(mockFailingMessageProcessor);
        irp.initialise();

        when(message.getPayload()).thenReturn(STRING_MESSAGE);
        when(event.getMessage()).thenReturn(message);

        try
        {
            irp.process(event);
        }
        catch (Exception e)
        {
        }
        irp.process(event);
    }

    public static class SerializationObjectStore implements ObjectStore<AtomicInteger>
    {

        private Map<Serializable,Serializable> store = new HashMap<Serializable,Serializable>();

        @Override
        public boolean contains(Serializable key) throws ObjectStoreException
        {
            return store.containsKey(key);
        }

        @Override
        public void store(Serializable key, AtomicInteger value) throws ObjectStoreException
        {
            store.put(key, SerializationUtils.serialize(value));
        }

        @Override
        public AtomicInteger retrieve(Serializable key) throws ObjectStoreException
        {
            Serializable serializable = store.get(key);
            return (AtomicInteger) SerializationUtils.deserialize((byte[]) serializable);
        }

        @Override
        public AtomicInteger remove(Serializable key) throws ObjectStoreException
        {
            Serializable serializable = store.remove(key);
            return (AtomicInteger) SerializationUtils.deserialize((byte[]) serializable);
        }

        @Override
        public boolean isPersistent()
        {
            return false;
        }
    }

    public static class InMemoryObjectStore implements ObjectStore<AtomicInteger>
    {
        private Map<Serializable,AtomicInteger> store = new HashMap<Serializable,AtomicInteger>();

        @Override
        public boolean contains(Serializable key) throws ObjectStoreException
        {
            return store.containsKey(key);
        }

        @Override
        public void store(Serializable key, AtomicInteger value) throws ObjectStoreException
        {
            store.put(key,value);
        }

        @Override
        public AtomicInteger retrieve(Serializable key) throws ObjectStoreException
        {
            return store.get(key);
        }

        @Override
        public AtomicInteger remove(Serializable key) throws ObjectStoreException
        {
            return store.remove(key);
        }

        @Override
        public boolean isPersistent()
        {
            return false;
        }
    }

}

