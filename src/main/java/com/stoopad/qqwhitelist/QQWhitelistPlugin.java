package com.stoopad.qqwhitelist;

import com.stoopad.qqwhitelist.listener.BotCommandListener;
import com.stoopad.qqwhitelist.listener.BindCodeCommand;
import com.stoopad.qqwhitelist.listener.JoinListener;
import com.stoopad.qqwhitelist.listener.ReloadCommand;
import com.stoopad.qqwhitelist.manager.BindManager;
import com.stoopad.qqwhitelist.manager.CodeManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class QQWhitelistPlugin extends JavaPlugin {

    private static QQWhitelistPlugin instance;
    private CodeManager codeManager;
    private BindManager bindManager;
    private String bindCommand;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 检查 HuHoBot
        Plugin huhoBot = getServer().getPluginManager().getPlugin("HuHoBot");
        if (huhoBot == null) {
            getLogger().severe("HuHoBot 未安装！禁用 HuHoSTDWhiteList");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 检查 API 可用性
        try {
            Class.forName("cn.huohuas001.huhobot.spigot.api.BotCustomCommand",
                    true, huhoBot.getClass().getClassLoader());
            getLogger().info("HuHoBot API 加载成功");
        } catch (ClassNotFoundException e) {
            getLogger().severe("HuHoBot API 加载失败: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        bindCommand = getConfig().getString("bind-command", "验证码");
        codeManager = new CodeManager(this);
        bindManager = new BindManager(this);

        // 注册事件 - BotCommandListener 通过反射注册，绕过类加载器隔离
        BotCommandListener botListener = new BotCommandListener(this);
        botListener.registerViaReflection();

        getServer().getPluginManager().registerEvents(new JoinListener(this), this);

        // 注册命令
        getCommand("bindcode").setExecutor(new BindCodeCommand(this));
        getCommand("huhostdwhitelist").setExecutor(new ReloadCommand(this));

        getLogger().info("HuHoSTDWhiteList 已加载");
    }

    @Override
    public void onDisable() {
        if (codeManager != null) codeManager.shutdown();
        getLogger().info("HuHoSTDWhiteList 已卸载");
    }

    public String getMessage(String key) {
        return getConfig().getString("messages." + key, key);
    }

    public static QQWhitelistPlugin getInstance() { return instance; }
    public CodeManager getCodeManager() { return codeManager; }
    public BindManager getBindManager() { return bindManager; }
    public String getBindCommand() { return bindCommand; }
}
