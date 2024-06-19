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
package bitronix.tm.mock.events;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author Ludovic Orban
 */
public class Chrono {

    private static long lastTime = 0;
    private static long counter = 0;
    private static final Lock synchronizationLock = new ReentrantLock();

    public static long getTime() {
        synchronizationLock.lock();
        try {
            long time = System.currentTimeMillis();
            if (time <= lastTime) {
                counter++;
                time += counter;
                lastTime += counter;
            } else {
                counter = 0;
                lastTime = time;
            }
            return time;
        } finally {
            synchronizationLock.unlock();
        }
    }

}
