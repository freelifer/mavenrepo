package freelifer.gradle.plugin;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author zhukun on 2020/9/25.
 */
public class _MavenRepoPluginTest {

    @Test
    public void testcreateDependency() {
        _MavenRepoPlugin plugin = new _MavenRepoPlugin();
        System.out.println(plugin.createMavenShell());
    }
}