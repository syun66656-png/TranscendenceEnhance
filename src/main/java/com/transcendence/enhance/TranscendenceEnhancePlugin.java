package com.transcendence.enhance;

import com.transcendence.enhance.command.TranscendenceCommand;
import com.transcendence.enhance.gui.TranscendenceGuiListener;
import com.transcendence.enhance.item.TranscendenceStoneFactory;
import com.transcendence.enhance.service.EnhancementService;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class TranscendenceEnhancePlugin extends JavaPlugin {

    private NamespacedKey transcendenceStoneKey;
    private EnhancementService enhancementService;
    private TranscendenceGuiListener guiListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.transcendenceStoneKey = new NamespacedKey(this, "transcendence_stone");
        this.enhancementService = new EnhancementService(this);
        TranscendenceStoneFactory stoneFactory = new TranscendenceStoneFactory(this, transcendenceStoneKey);

        this.guiListener = new TranscendenceGuiListener(this, enhancementService, transcendenceStoneKey);
        getServer().getPluginManager().registerEvents(guiListener, this);

        TranscendenceCommand command = new TranscendenceCommand(guiListener, stoneFactory);
        if (getCommand("transcendence") != null) {
            getCommand("transcendence").setExecutor(command);
            getCommand("transcendence").setTabCompleter(command);
        } else {
            getLogger().warning("Command 'transcendence' not found in plugin.yml");
        }
    }

    @Override
    public void onDisable() {
        if (guiListener != null) {
            guiListener.shutdownAndRefundAll();
        }
    }

    public NamespacedKey getTranscendenceStoneKey() {
        return transcendenceStoneKey;
    }
}
