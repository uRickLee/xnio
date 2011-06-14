/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio.nio.test;

import java.util.List;
import org.xnio.ChannelListener;
import java.nio.channels.Channel;

final class CatchingChannelListener<T extends Channel> implements ChannelListener<T> {

    private final ChannelListener<? super T> delegate;
    private final List<Throwable> problems;

    CatchingChannelListener(final ChannelListener<? super T> delegate, final List<Throwable> problems) {
        this.delegate = delegate;
        this.problems = problems;
    }

    public void handleEvent(final T channel) {
        try {
            if (delegate != null) delegate.handleEvent(channel);
        } catch (RuntimeException t) {
            problems.add(t);
            throw t;
        } catch (Error t) {
            problems.add(t);
            throw t;
        }
    }
}
