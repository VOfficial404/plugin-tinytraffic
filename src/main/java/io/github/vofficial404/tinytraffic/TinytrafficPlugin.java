package io.github.vofficial404.tinytraffic;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Plugin main class to manage the lifecycle of the plugin.
 *
 * <p>This class must be public and have a public constructor.
 *
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.
 *
 * @author VOfficial404
 * @since 1.0.0
 */
@Component
public class TinytrafficPlugin extends BasePlugin {

    public TinytrafficPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
