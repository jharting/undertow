/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.servlet.test.listener.ordering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;

import io.undertow.server.handlers.CookieHandler;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ListenerInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.test.SimpleServletTestCase;
import io.undertow.servlet.test.util.MessageServlet;
import io.undertow.servlet.test.util.TestClassIntrospector;
import io.undertow.servlet.test.util.TestResourceLoader;
import io.undertow.servlet.test.util.Tracker;
import io.undertow.test.utils.DefaultServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import io.undertow.util.TestHttpClient;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @see https://issues.jboss.org/browse/UNDERTOW-23
 *
 * @author Jozef Hartinger
 *
 */
@RunWith(DefaultServer.class)
public class ServletSessionTestCase {

    @BeforeClass
    public static void setup() throws ServletException {

        final CookieHandler cookieHandler = new CookieHandler();
        final PathHandler path = new PathHandler();
        cookieHandler.setNext(path);
        final ServletContainer container = ServletContainer.Factory.newInstance();

        DeploymentInfo builder = new DeploymentInfo()
                .setClassLoader(SimpleServletTestCase.class.getClassLoader())
                .setContextPath("/listener")
                .setClassIntrospecter(TestClassIntrospector.INSTANCE)
                .setDeploymentName("listener.war")
                .setResourceLoader(TestResourceLoader.NOOP_RESOURCE_LOADER)
                .addListener(new ListenerInfo(FirstListener.class))
                .addListener(new ListenerInfo(SecondListener.class))
                .addServlet(
                        new ServletInfo("message", MessageServlet.class)
                        .addMapping("/*")
                        .addInitParam(MessageServlet.MESSAGE, "foo"));

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        path.addPath(builder.getContextPath(), manager.start());

        DefaultServer.setRootHandler(cookieHandler);
    }

    @Test
    public void testSimpleSessionUsage() throws IOException {
        TestHttpClient client = new TestHttpClient();
        try {
            Tracker.reset();
            HttpGet get = new HttpGet(DefaultServer.getDefaultServerURL() + "/listener/test");
            HttpResponse result = client.execute(get);
            Assert.assertEquals(200, result.getStatusLine().getStatusCode());

            List<String> expected = new ArrayList<String>();
            expected.add(FirstListener.class.getSimpleName());
            expected.add(SecondListener.class.getSimpleName());
            expected.add(SecondListener.class.getSimpleName());
            expected.add(FirstListener.class.getSimpleName());

            Assert.assertEquals(expected, Tracker.getActions());
        } finally {
            client.getConnectionManager().shutdown();
        }
    }
}
