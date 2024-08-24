package org.bitstrings.maven.plugins.dockerrun;

import static com.soebes.itf.jupiter.extension.MavenCLIOptions.NO_TRANSFER_PROGRESS;

import com.soebes.itf.extension.assertj.MavenITAssertions;
import com.soebes.itf.jupiter.extension.MavenGoal;
import com.soebes.itf.jupiter.extension.MavenJupiterExtension;
import com.soebes.itf.jupiter.extension.MavenOption;
import com.soebes.itf.jupiter.extension.MavenTest;
import com.soebes.itf.jupiter.maven.MavenExecutionResult;

@MavenJupiterExtension
@MavenOption(NO_TRANSFER_PROGRESS)
public class DockerRunMojoIT
{
    @MavenTest
    @MavenGoal("verify")
    void run(MavenExecutionResult result)
    {
        MavenITAssertions.assertThat(result).isSuccessful();
    }

    @MavenTest
    @MavenGoal("verify")
    void run_sub_module(MavenExecutionResult result)
    {
        MavenITAssertions.assertThat(result).isSuccessful();
    }
}
