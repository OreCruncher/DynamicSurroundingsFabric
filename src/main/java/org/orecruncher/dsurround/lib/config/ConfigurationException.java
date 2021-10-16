package org.orecruncher.dsurround.lib.config;

public class ConfigurationException extends Exception {

    public ConfigurationException(String format, Object... params) {
        super(String.format(format, params));
    }
}
