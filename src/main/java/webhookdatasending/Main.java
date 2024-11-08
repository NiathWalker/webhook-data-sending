package webhookdatasending;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.mod.Plugin;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class Main extends Plugin {
    private final HashMap<String, String> configValues = new HashMap<>();

    @Override
    public void init() {
        Events.on(EventType.ServerLoadEvent.class, e -> {
            File configPath = new File(Core.settings.getDataDirectory().child("mods/webhook-chat-sending.properties").toString());
            try {
                if (!configPath.exists()) {
                    if (configPath.createNewFile()) {
                        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configPath), StandardCharsets.UTF_8))) {
                            StringBuilder data = new StringBuilder();
                            data.append("webhook-link-indicate=https://discord.com/api/webhooks/").append("\n");
                            data.append("webhook-server-mode-indicate=survival").append("\n");
                            data.append("webhook-admin-detection-toggle=false").append("\n");
                            data.append("webhook-message-sending-toggle=false").append("\n");
                            data.append("webhook-connection-sending-toggle=false").append("\n");
                            data.append("webhook-disconnection-sending-toggle=false");

                            writer.write(data.toString());
                        }
                    }
                }
            } catch (IOException ex) {
                Log.err(String.format("An error occurred while creating the settings file: %s", ex.getMessage()));
            }

            settingsLoading(configPath);
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            if (!e.message.startsWith("/") ) {
                if (configValues.get("webhook-message-sending-toggle").equals("true")) {
                    if (configValues.get("webhook-admin-detection-toggle").equals("true")) {
                        if (e.player.admin) {
                            webhook(String.format("[message] [admin] [%s]: %s", e.player.plainName(), e.message));
                        } else {
                            webhook(String.format("[message] [player] [%s]: %s", e.player.plainName(), e.message));
                        }
                    } else {
                        webhook(String.format("[message] [%s]: %s", e.player.plainName(), e.message));
                    }
                }
            }
        });

        Events.on(EventType.PlayerJoin.class, e -> {
            if (configValues.get("webhook-connection-sending-toggle").equals("true")) {
                if (configValues.get("webhook-admin-detection-toggle").equals("true")) {
                    if (e.player.admin) {
                        webhook(String.format("[connection] [admin] [%s]", e.player.plainName()));
                    } else {
                        webhook(String.format("[connection] [player] [%s]", e.player.plainName()));
                    }
                } else {
                    webhook(String.format("[connection] [%s]", e.player.plainName()));
                }
            }
        });

        Events.on(EventType.PlayerLeave.class, e -> {
            if (configValues.get("webhook-disconnection-sending-toggle").equals("true")) {
                if (configValues.get("webhook-admin-detection-toggle").equals("true")) {
                    if (e.player.admin) {
                        webhook(String.format("[disconnection] [admin] [%s]", e.player.plainName()));
                    } else {
                        webhook(String.format("[disconnection] [player] [%s]", e.player.plainName()));
                    }
                } else {
                    webhook(String.format("[disconnection] [%s]", e.player.plainName()));
                }
            }
        });
    }

    public void registerServerCommands(CommandHandler handler) {
        handler.register("webhook-reload", "Reload the plugin configuration.", arg -> {
            settingsLoading(new File(Core.settings.getDataDirectory().child("mods/webhook-chat-sending.properties").toString()));
            Log.info("The configuration of the \"webhook-chat-sending\" plugin has been reloaded.");
        });
    }

    private void webhook(String message) {
        try {
            String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            JSONObject json = new JSONObject();
            json.put("content", String.format("[%s] [%s] %s", dateTime, configValues.get("webhook-server-mode-indicate"), message));

            CompletableFuture.runAsync(() -> {
                try {
                    URL url = new URL(configValues.get("webhook-link-indicate"));

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);

                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
                    writer.print(json);
                    writer.flush();
                    writer.close();

                    int responseCode = connection.getResponseCode();

                    if (responseCode != 204) {
                        Log.err(String.format("An error occurred when requesting the webhook: %s", responseCode));
                    }

                    connection.disconnect();
                } catch (Exception e) {
                    Log.err(String.format("An error occurred when sending a webhook message: %s", e.getMessage()));
                }
            });
        } catch (JSONException e) {
            Log.err(String.format("Failed to create a JSON object to send the webhook: %s", e.getMessage()));
        }
    }

    private void settingsLoading(File configPath) {
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configPath), StandardCharsets.UTF_8)) {
            properties.load(reader);

            configValues.put("webhook-link-indicate", properties.getProperty("webhook-link-indicate"));
            configValues.put("webhook-server-mode-indicate", properties.getProperty("webhook-server-mode-indicate"));
            configValues.put("webhook-admin-detection-toggle", properties.getProperty("webhook-admin-detection-toggle"));
            configValues.put("webhook-message-sending-toggle", properties.getProperty("webhook-chat-sending-toggle"));
            configValues.put("webhook-connection-sending-toggle", properties.getProperty("webhook-join-sending-toggle"));
            configValues.put("webhook-disconnection-sending-toggle", properties.getProperty("webhook-leave-sending-toggle"));
        } catch (IOException ex) {
            Log.err(String.format("An error occurred while reading the settings file: %s", ex.getMessage()));
        }
    }
}