package com.transcendence.enhance;

import com.transcendence.enhance.command.TranscendenceCommand;
import com.transcendence.enhance.gui.TranscendenceGuiListener;
import com.transcendence.enhance.item.EnhancementMaterialFactory;
import com.transcendence.enhance.service.EnhancementService;
import org.bukkit.plugin.java.JavaPlugin;

public final class TranscendenceEnhancePlugin extends JavaPlugin {

    private EnhancementService enhancementService;
    private EnhancementMaterialFactory materialFactory;
    private TranscendenceGuiListener guiListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.enhancementService = new EnhancementService(this);
        this.materialFactory = new EnhancementMaterialFactory(this);

        this.guiListener = new TranscendenceGuiListener(this, enhancementService, materialFactory);
        getServer().getPluginManager().registerEvents(guiListener, this);

        TranscendenceCommand command = new TranscendenceCommand(guiListener, materialFactory);
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

}
