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

import jakarta.jms.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 * @author Ludovic Orban
 */
public class MockConnectionFactory implements ConnectionFactory {

    public Connection createConnection() throws JMSException {
    	Answer<Session> sessionAnswer = new Answer<Session>() {
			public Session answer(InvocationOnMock invocation) throws Throwable {
				Session session = mock(Session.class);
				MessageProducer producer = mock(MessageProducer.class);
				when(session.createProducer((Destination) any())).thenReturn(producer);
				return session;
			}
    	};

    	Connection connection = mock(Connection.class);
    	when(connection.createSession(anyBoolean(), anyInt())).thenAnswer(sessionAnswer);
    	return connection;
    }

    public Connection createConnection(String jndiName, String jndiName1) throws JMSException {
        return createConnection();
    }

	@Override
	public JMSContext createContext() {
		return null;
	}

	@Override
	public JMSContext createContext(String userName, String password) {
		return null;
	}

	@Override
	public JMSContext createContext(String userName, String password, int sessionMode) {
		return null;
	}

	@Override
	public JMSContext createContext(int sessionMode) {
		return null;
	}
}