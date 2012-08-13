/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpHandler;
import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.spec.HttpServletRequestImpl;
import io.undertow.servlet.spec.HttpServletResponseImpl;

/**
 * @author Stuart Douglas
 */
public class FilterHandler implements BlockingHttpHandler {

    private final List<ManagedFilter> filters;

    private final BlockingHttpHandler next;

    public FilterHandler(final List<ManagedFilter> filters, final BlockingHttpHandler next) {
        this.next = next;
        this.filters = new ArrayList<ManagedFilter>(filters);
    }

    @Override
    public void handleRequest(final BlockingHttpServerExchange exchange) throws Exception {
        HttpServletRequestImpl request = (HttpServletRequestImpl) exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        HttpServletResponseImpl response = (HttpServletResponseImpl) exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        FilterChainImpl filterChain = new FilterChainImpl(exchange);
        filterChain.doFilter(request, response);
    }

    private class FilterChainImpl implements FilterChain {

        int location = 0;
        final BlockingHttpServerExchange exchange;

        private FilterChainImpl(final BlockingHttpServerExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void doFilter(final ServletRequest request, final ServletResponse response) throws IOException, ServletException {
            try {

                int index = location++;
                if (index >= filters.size()) {
                    next.handleRequest(exchange);
                } else {
                    filters.get(index).doFilter(request, response, this);
                }
            } catch (IOException e) {
                throw e;
            } catch (ServletException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                location--;
            }
        }
    }
}
