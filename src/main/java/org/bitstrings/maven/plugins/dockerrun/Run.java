package org.bitstrings.maven.plugins.dockerrun;

import static org.bitstrings.maven.plugins.dockerrun.Run.ImagePullPolicy.ALWAYS;

import java.util.List;
import java.util.Properties;

import lombok.Getter;
import lombok.Setter;

public class Run
{
    @Getter
    @Setter
    private String image;

    @Getter
    @Setter
    private ImagePullPolicy imagePullPolicy = ALWAYS;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private Properties env;

    @Getter
    @Setter
    private boolean autoRemove = true;

    @Getter
    @Setter
    private boolean init = false;

    @Getter
    @Setter
    private boolean tty = false;

    @Getter
    @Setter
    private List<String> args;

    @Getter
    @Setter
    private List<String> dns;

    @Getter
    @Setter
    private List<String> dnsSearch;

    @Getter
    @Setter
    private String domainName;

    @Getter
    @Setter
    private Volumes volumes;

    @Getter
    @Setter
    private String user;

    @Getter
    @Setter
    private boolean setUserToCurrent = false;

    @Getter
    @Setter
    private boolean echoStdOut = true;

    @Getter
    @Setter
    private boolean echoStdErr = true;

    @Getter
    @Setter
    private String alias;

    public enum ImagePullPolicy
    {
        NEVER, IF_NOT_PRESENT, ALWAYS
    }
}
