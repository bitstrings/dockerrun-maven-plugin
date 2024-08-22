/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitstrings.maven.plugins.dockerrun;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_INTEGRATION_TEST;
import static org.bitstrings.maven.plugins.dockerrun.DockerRunMojo.ImagePullPolicy.ALWAYS;
import static org.bitstrings.maven.plugins.dockerrun.DockerRunMojo.ImagePullPolicy.IF_NOT_PRESENT;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import oshi.SystemInfo;
import oshi.software.os.OSProcess;

@Mojo(
    name = "run",
    defaultPhase = PRE_INTEGRATION_TEST,
    threadSafe = true,
    requiresProject = true,
    requiresOnline = false
)
public class DockerRunMojo
    extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession mavenSession;

    @Parameter(defaultValue = "false")
    private boolean quiet;

    @Parameter(required = true)
    private String image;

    @Parameter(defaultValue = "ALWAYS")
    private ImagePullPolicy imagePullPolicy;

    @Parameter
    private String name;

    @Parameter
    private Properties env;

    @Parameter(defaultValue = "true")
    private boolean autoRemove;

    @Parameter(defaultValue = "false")
    private boolean init;

    @Parameter(defaultValue = "false")
    private boolean tty;

    @Parameter
    private List<String> args;

    @Parameter
    private List<String> dns;

    @Parameter
    private List<String> dnsSearch;

    @Parameter
    private String domainName;

    @Parameter(defaultValue = "true")
    private boolean echoStdOut;

    @Parameter(defaultValue = "true")
    private boolean echoStdErr;

    @Parameter
    private Volumes volumes;

    @Parameter
    private String user;

    @Parameter
    private boolean setUserToCurrent;

    public enum ImagePullPolicy
    {
        NEVER, IF_NOT_PRESENT, ALWAYS
    }

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if (setUserToCurrent)
        {
            OSProcess osProcess = new SystemInfo().getOperatingSystem().getCurrentProcess();

            user = osProcess.getUserID() + ":" + osProcess.getGroupID();
        }

        String[] imageParts = StringUtils.split(image, ':');

        String imageName = ArrayUtils.get(imageParts, 0);
        String imageTag = ArrayUtils.get(imageParts, 1, "latest");

        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .build();

        DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(dockerClientConfig.getDockerHost())
            .sslConfig(dockerClientConfig.getSSLConfig())
            .connectionTimeout(Duration.ofSeconds(60))
            .maxConnections(100)
            .responseTimeout(Duration.ofSeconds(60))
            .build();

        DockerClient dockerClient = DockerClientBuilder.getInstance(dockerClientConfig)
            .withDockerHttpClient(dockerHttpClient)
            .build();

        List<Image> currentImages = dockerClient.listImagesCmd().withImageNameFilter(image).exec();

        HostConfig hostConfig = new HostConfig()
            .withInit(init)
            .withAutoRemove(autoRemove);

        if (dns != null)
        {
            hostConfig.withDns(dns);
        }

        if (dnsSearch != null)
        {
            hostConfig.withDnsSearch(dnsSearch);
        }

        if (volumes != null)
        {
            hostConfig.withBinds(getBindsFromVolumesParam(volumes));
        }

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image)
            .withHostConfig(hostConfig)
            .withTty(tty);

        if (name != null)
        {
            containerCmd.withName(name);
        }

        if (args != null)
        {
            containerCmd.withCmd(args);
        }

        if (env != null)
        {
            containerCmd.withEnv(
                env.entrySet().stream()
                    .map(prop -> prop.getKey() + "=" + prop.getValue())
                    .collect(Collectors.toList())
            );
        }

        if (domainName != null)
        {
            containerCmd.withDomainName(domainName);
        }

        if (user != null)
        {
            containerCmd.withUser(user);
        }

        CreateContainerResponse createContainerResponse = null;

        boolean retry = true;
        boolean pullImage = (imagePullPolicy == ALWAYS);

        do
        {
            if (pullImage)
            {
                try
                {
                    retry = false;

                    pullImage(dockerClient, imageName, imageTag, new PullImageResultCallback());
                    pullImage = false;
                }
                catch (InterruptedException e)
                {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }

            try
            {
                createContainerResponse = containerCmd.exec();
            }
            catch (NotFoundException e)
            {
                pullImage = (imagePullPolicy == IF_NOT_PRESENT);

                if (pullImage)
                {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
        while (retry);

        dockerClient.startContainerCmd(createContainerResponse.getId()).exec();

        try
        {
            dockerClient.logContainerCmd(createContainerResponse.getId())
                .withStdOut(true)
                .withStdErr(true)
                .withTailAll()
                .withFollowStream(true)
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Frame object)
                    {
                        StreamType streamType = object.getStreamType();

                        if (
                            (echoStdOut && (streamType == StreamType.STDOUT))
                                || (echoStdErr && (streamType == StreamType.STDERR))
                        )
                        {
                            System.out.print(new String(object.getPayload()));
                        }
                    }
                }
            ).awaitCompletion();
        }
        catch (InterruptedException e)
        {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    protected void pullImage(DockerClient dockerClient, String repository, String tag, PullImageResultCallback callback)
        throws InterruptedException
    {
        dockerClient.pullImageCmd(repository)
            .withTag(tag)
            .exec(callback)
            .awaitCompletion();
    }

    protected List<Bind> getBindsFromVolumesParam(Volumes volumes)
        throws MojoFailureException
    {
        List<Bind> binds = new ArrayList<>();

        for (String volumeBind : volumes.getBind().getVolumes())
        {
            String[] paths = StringUtils.split(volumeBind, ":", 2);

            if (paths.length != 2)
            {
                throw new MojoFailureException(
                    "Volume bind " + volumeBind + " should contain exactly two paths [source]:[destination]."
                );
            }

            binds.add(new Bind(paths[0], new Volume(paths[1])));
        }

        return binds;
    }
}
