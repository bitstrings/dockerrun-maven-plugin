package org.bitstrings.maven.plugins.dockerrun.util;

import java.lang.reflect.Proxy;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.bitstrings.maven.plugins.dockerrun.DockerRunMojo;
import org.bitstrings.maven.plugins.dockerrun.Run;

public final class MavenUtils
{
    private MavenUtils()
    {
    }

    private static String getDataKey(String key)
    {
        return DockerRunMojo.class.getName() + ":" + key;
    }

    public static Data<Run> getRequestDockerRunData(MavenSession session)
    {
        return getRequestDockerRunData(session.getRequest());
    }

    public static Data<Run> getRequestDockerRunData(MavenExecutionRequest request)
    {
        Data<Run> dockerRunData = getRequestData(request, StringUtils.EMPTY, null);

        if (dockerRunData == null)
        {
            dockerRunData = new Data<>();

            setRequestData(request, StringUtils.EMPTY, dockerRunData);
        }

        return dockerRunData;
    }

    public static <T> T getRequestData(MavenSession session, String key)
    {
        return getRequestData(session.getRequest(), key);
    }

    public static <T> T getRequestData(MavenExecutionRequest request, String key)
    {
        return getRequestData(request, key, null);
    }

    public static <T> T getRequestData(MavenSession session, String key, T defaultValue)
    {
        return getRequestData(session.getRequest(), key, defaultValue);
    }

    public static <T> T getRequestData(MavenExecutionRequest request, String key, T defaultValue)
    {
        return (T) request.getData().getOrDefault(getDataKey(key), defaultValue);
    }

    public static void setRequestData(MavenSession session, String key, Object value)
    {
        setRequestData(session.getRequest(), key, value);
    }

    public static void setRequestData(MavenExecutionRequest request, String key, Object value)
    {
        request.getData().put(getDataKey(key), value);
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
