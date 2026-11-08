package me.sodiumlegacy.mod.client.gui.options.storage;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

public class MinecraftOptionsStorage implements OptionStorage<GameOptions> {
    private final MinecraftClient client;

    public MinecraftOptionsStorage() {
        this.client = MinecraftClient.getInstance();
    }

    @Override
    public GameOptions getData() {
        return this.client.options;
    }

    @Override
    public void save() {
        this.getData().save();

        SodiumClientMod.logger().info("Flushed changes to Minecraft configuration");
    }
}
