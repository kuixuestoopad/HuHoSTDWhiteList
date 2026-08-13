package com.stoopad.qqwhitelist.listener;

import com.stoopad.qqwhitelist.QQWhitelistPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 通过反射注册事件处理器，绕过 Paper 类加载器隔离问题
 */
public class BotCommandListener implements Listener {

    private final QQWhitelistPlugin plugin;

    public BotCommandListener(QQWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 在插件启用时调用，通过反射注册 BotCustomCommand 事件处理器
     */
    public void registerViaReflection() {
        try {
            Plugin huhoBot = Bukkit.getPluginManager().getPlugin("HuHoBot");
            ClassLoader huhoCL = huhoBot.getClass().getClassLoader();

            // 用 HuHoBot 的类加载器加载 BotCustomCommand
            Class<?> botCmdClass = Class.forName(
                    "cn.huohuas001.huhobot.spigot.api.BotCustomCommand",
                    true, huhoCL);

            // 获取 BotCustomCommand 的 HandlerList（静态方法）
            Method getHandlerListMethod = botCmdClass.getMethod("getHandlerList");
            HandlerList handlerList = (HandlerList) getHandlerListMethod.invoke(null);

            // 创建 EventExecutor，内部用反射调用我们的处理逻辑
            EventExecutor executor = (listener, event) -> {
                if (botCmdClass.isInstance(event)) {
                    try {
                        handleBotCommand(botCmdClass, event);
                    } catch (Exception e) {
                        throw new EventException(e);
                    }
                }
            };

            // 注册到 BotCustomCommand 的 HandlerList
            RegisteredListener registeredListener = new RegisteredListener(
                    this, executor, EventPriority.NORMAL, huhoBot, false);
            handlerList.register(registeredListener);
            handlerList.bake();

            plugin.getLogger().info("BotCustomCommand 事件处理器注册成功 (reflection)");

        } catch (Exception e) {
            plugin.getLogger().severe("注册 BotCustomCommand 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleBotCommand(Class<?> botCmdClass, Object event) throws Exception {
        String command = (String) botCmdClass.getMethod("getCommand").invoke(event);
        List<String> params = (List<String>) botCmdClass.getMethod("getParam").invoke(event);

        if (plugin.getBindCommand().equals(command)) {
            handleBindCode(botCmdClass, event, params);
        }
    }

    private void handleBindCode(Class<?> botCmdClass, Object event, List<String> params) throws Exception {
        botCmdClass.getMethod("setCancelled", boolean.class).invoke(event, true);

        Object data = botCmdClass.getMethod("getData").invoke(event);

        Method getJSONObject = data.getClass().getMethod("getJSONObject", String.class);
        Object author = getJSONObject.invoke(data, "author");

        Method getString = author.getClass().getMethod("getString", String.class);
        String openId = (String) getString.invoke(author, "openId");

        if (params.isEmpty()) {
            botCmdClass.getMethod("respone", String.class, String.class)
                    .invoke(event, plugin.getMessage("usage"), "success");
            return;
        }

        String code = params.get(0);

        if (!plugin.getBindManager().canBind(openId)) {
            botCmdClass.getMethod("respone", String.class, String.class)
                    .invoke(event, plugin.getMessage("bind-limit")
                            .replace("{max}", String.valueOf(plugin.getBindManager().getMaxAccountsPerQQ())), "success");
            return;
        }

        String playerName = plugin.getCodeManager().consumeCode(code);
        if (playerName == null) {
            botCmdClass.getMethod("respone", String.class, String.class)
                    .invoke(event, plugin.getMessage("invalid-code"), "success");
            return;
        }

        if (plugin.getBindManager().isBound(playerName)) {
            botCmdClass.getMethod("respone", String.class, String.class)
                    .invoke(event, plugin.getMessage("already-bound")
                            .replace("{player}", playerName), "success");
            return;
        }

        boolean success = plugin.getBindManager().bind(playerName, openId);
        if (!success) {
            botCmdClass.getMethod("respone", String.class, String.class)
                    .invoke(event, plugin.getMessage("already-bound")
                            .replace("{player}", playerName), "success");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            offlinePlayer.setWhitelisted(true);
            plugin.getLogger().info("已为 " + playerName + " 添加白名单（QQ绑定 by " + openId + "）");
        });

        botCmdClass.getMethod("respone", String.class, String.class)
                .invoke(event, plugin.getMessage("success")
                        .replace("{player}", playerName), "success");
    }
}
