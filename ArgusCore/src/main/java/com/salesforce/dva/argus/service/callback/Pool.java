/*
 * Copyright (C) 2016 Kiwigrid GmbH (oss@kiwigrid.com)
 *
 * Licensed under the  Creative Commons - Attribution-NoDerivatives License, Version 4.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *         https://creativecommons.org/licenses/by-nd/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.salesforce.dva.argus.service.callback;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages a pool of objects where a predefined number of objects are created on demand. Requests of more elements
 * will be deferred until the next object will be returned from previous usage.
 *
 * @author svenkrause
 */
public abstract class Pool<T> {
	private ConcurrentLinkedQueue<T> pool;

	private ScheduledExecutorService executorService;

	/**
	 * Creates the pool.
	 *
	 * @param minIdle minimum number of objects residing in the pool
	 */
	public Pool(final int minIdle) {
		// initialize pool
		initialize(minIdle);
	}

	/**
	 * Creates the pool.
	 *
	 * @param minIdle            minimum number of objects residing in the pool
	 * @param maxIdle            maximum number of objects residing in the pool
	 * @param validationInterval time in seconds for periodical checking of minIdle / maxIdle conditions in a separate thread.
	 *                           When the number of objects is less than minIdle, missing instances will be created.
	 *                           When the number of objects is greater than maxIdle, too many instances will be removed.
	 * @param unit               The time unit to use
	 */
	public Pool(final int minIdle, final int maxIdle, final long validationInterval, final TimeUnit unit) {
		// initialize pool
		initialize(minIdle);

		// check pool conditions in a separate thread
		executorService = Executors.newSingleThreadScheduledExecutor();
		executorService.scheduleWithFixedDelay(new Runnable()
		{
			@Override
			public void run() {
				int size = pool.size();
				if (size < minIdle) {
					int sizeToBeAdded = minIdle - size;
					for (int i = 0; i < sizeToBeAdded; i++) {
						pool.add(createObject());
					}
				} else if (size > maxIdle) {
					int sizeToBeRemoved = size - maxIdle;
					for (int i = 0; i < sizeToBeRemoved; i++) {
						pool.poll();
					}
				}
			}
		}, validationInterval, validationInterval, unit);
	}

	/**
	 * Gets the next free object from the pool. If the pool doesn't contain any objects,
	 * a new object will be created and given to the caller of this method back.
	 *
	 * @return T borrowed object
	 */
	public T borrowObject() {
		T object;
		if ((object = pool.poll()) == null) {
			object = createObject();
		}

		return object;
	}

	/**
	 * Returns object back to the pool.
	 *
	 * @param object object to be returned
	 */
	public void returnObject(T object) {
		if (object == null) {
			return;
		}

		this.pool.offer(object);
	}

	/**
	 * Shutdown this pool.
	 */
	public void shutdown() {
		if (executorService != null) {
			executorService.shutdown();
		}
	}

	/**
	 * Creates a new object.
	 *
	 * @return T new object
	 */
	protected abstract T createObject();

	private void initialize(final int minIdle) {
		pool = new ConcurrentLinkedQueue<T>();

		for (int i = 0; i < minIdle; i++) {
			pool.add(createObject());
		}
	}
}