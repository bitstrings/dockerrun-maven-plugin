package org.bitstrings.maven.plugins.dockerrun;

import java.lang.reflect.Proxy;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;

public final class MavenUtils
{
    private MavenUtils()
    {
    }

    public static LinkedMap<String, Run> getRequestDockerRunData(MavenExecutionRequest request)
    {
        return getRequestData(request, DockerRunMojo.class.getName(), null);
    }

    public static <T> T getRequestData(MavenExecutionRequest request, String key)
    {
        return getRequestData(request, key, null);
    }

    public static <T> T getRequestData(MavenExecutionRequest request, String key, T defaultValue)
    {
        return (T) request.getData().getOrDefault(key, defaultValue);
    }

    public static boolean proxyExecutionListener(
        MavenSession mavenSession, boolean ifNotProxied, ExecutionListener executionListenerProxy
    )
    {
        MavenExecutionRequest mavenExecutionRequest = mavenSession.getRequest();
        ExecutionListener executionListener = mavenExecutionRequest.getExecutionListener();

        if (ifNotProxied && Proxy.isProxyClass(executionListener.getClass()))
        {
            return false;
        }

        ExecutionListener proxyListener = (ExecutionListener) Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class[] { ExecutionListener.class },
            (proxy, method, args) -> {
                // current API always returns Void.TYPE
                method.invoke(executionListenerProxy, args);

                return method.invoke(executionListener, args);
            }
        );

        mavenExecutionRequest.setExecutionListener(proxyListener);

        return true;
    }
}
