/*
 * Copyright (C) 2006-2013 Bitronix Software (http://www.bitronix.be)
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
package bitronix.tm.mock.resource.jms;

import bitronix.tm.mock.resource.MockXAResource;
import jakarta.jms.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * @author Ludovic Orban
 */
public class MockXAConnectionFactory implements XAConnectionFactory {

    private static JMSException staticCloseXAConnectionException;
    private static JMSException staticCreateXAConnectionException;

    private String endPoint;

    public XAConnection createXAConnection() throws JMSException {
        if (staticCreateXAConnectionException != null)
            throw staticCreateXAConnectionException;

    	Answer<XASession> xaSessionAnswer = invocation -> {
			XASession mockXASession = mock(XASession.class);
			MessageProducer messageProducer = mock(MessageProducer.class);
			when(mockXASession.createProducer(any())).thenReturn(messageProducer);
			MessageConsumer messageConsumer = mock(MessageConsumer.class);
			when(mockXASession.createConsumer(any())).thenReturn(messageConsumer);
			when(mockXASession.createConsumer(any(), anyString())).thenReturn(messageConsumer);
			when(mockXASession.createConsumer(any(), anyString(), anyBoolean())).thenReturn(messageConsumer);
			Queue queue = mock(Queue.class);
			when(mockXASession.createQueue(anyString())).thenReturn(queue);
			Topic topic = mock(Topic.class);
			when(mockXASession.createTopic(anyString())).thenReturn(topic);
			MockXAResource mockXAResource = new MockXAResource(null);
			when(mockXASession.getXAResource()).thenReturn(mockXAResource);
			Answer<Session> sessionAnswer = invocation1 -> {
				Session session = mock(Session.class);
				MessageProducer producer = mock(MessageProducer.class);
				when(session.createProducer(any())).thenReturn(producer);
				return session;
			};
			when(mockXASession.getSession()).thenAnswer(sessionAnswer);

			return mockXASession;
		};

    	XAConnection mockXAConnection = mock(XAConnection.class);
    	when(mockXAConnection.createXASession()).thenAnswer(xaSessionAnswer);
    	when(mockXAConnection.createSession(anyBoolean(), anyInt())).thenAnswer(xaSessionAnswer);
        if (staticCloseXAConnectionException != null)
            doThrow(staticCloseXAConnectionException).when(mockXAConnection).close();

        return mockXAConnection;
    }

    public XAConnection createXAConnection(String jndiName, String jndiName1) throws JMSException {
        return createXAConnection();
    }

	@Override
	public XAJMSContext createXAContext() {
		return null;
	}

	@Override
	public XAJMSContext createXAContext(String userName, String password) {
		return null;
	}

	public static void setStaticCloseXAConnectionException(JMSException e) {
        staticCloseXAConnectionException = e;
    }

    public static void setStaticCreateXAConnectionException(JMSException e) {
        staticCreateXAConnectionException = e;
    }

    public String getEndpoint() {
        return endPoint;
    }

    public void setEndpoint(String endPoint) {
        this.endPoint = endPoint;
    }
}
