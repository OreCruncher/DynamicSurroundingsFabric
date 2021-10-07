package org.orecruncher.dsurround.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.serializer.ConfigSerializer;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.FrameworkUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Hack around AutoConfig's assumption about where I want to put a config file and the
 * resource translation ID that it generates.  Would be nice if there was some sort of
 * factory supplier that can be passed into the system to generate the necessary path.
 * @param <T>
 */
public class MyGsonConfigSerializer<T extends ConfigData> implements ConfigSerializer<T> {
    private final Config definition;
    private final Class<T> configClass;
    private final Gson gson;

    public MyGsonConfigSerializer(Config definition, Class<T> configClass, Gson gson) {
        this.definition = definition;
        this.configClass = configClass;
        this.gson = gson;
    }

    public MyGsonConfigSerializer(Config definition, Class<T> configClass) {
        this(definition, configClass, (new GsonBuilder()).setPrettyPrinting().create());
    }

    private Path getConfigPath() {
        return FrameworkUtils.getConfigPath(Client.ModId).resolve(this.definition.name() + ".json");
    }

    public void serialize(T config) throws SerializationException {
        Path configPath = this.getConfigPath();

        try {
            Files.createDirectories(configPath.getParent());
            BufferedWriter writer = Files.newBufferedWriter(configPath);
            this.gson.toJson(config, writer);
            writer.close();
        } catch (IOException var4) {
            throw new SerializationException(var4);
        }
    }

    public T deserialize() throws SerializationException {
        Path configPath = this.getConfigPath();
        if (Files.exists(configPath)) {
            try {
                BufferedReader reader = Files.newBufferedReader(configPath);
                T ret = this.gson.fromJson(reader, this.configClass);
                reader.close();
                return ret;
            } catch (JsonParseException | IOException var4) {
                throw new SerializationException(var4);
            }
        } else {
            return this.createDefault();
        }
    }

    public T createDefault() {
        try {
            Constructor<T> constructor = this.configClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException var2) {
            throw new RuntimeException(var2);
        }
    }
}
