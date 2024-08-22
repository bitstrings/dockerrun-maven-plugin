package org.bitstrings.maven.plugins.dockerrun;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;

public final class MavenUtils
{
    private MavenUtils()
    {
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
            new InvocationHandler()
            {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable
                {
                    // current API always returns Void.TYPE
                    // but lets futureproof it?
                    Object proxyReturn = method.invoke(executionListenerProxy, args);

                    return Void.TYPE.equals(proxyReturn) ? method.invoke(executionListener, args) : proxyReturn;
                }
            }
        );

        mavenExecutionRequest.setExecutionListener(proxyListener);

        return true;
    }
}
