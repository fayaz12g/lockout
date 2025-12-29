package one.fayaz;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Lockout implements ModInitializer {
    public static final String MOD_ID = "lockout";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<String, Integer> NAMED_COLORS = new HashMap<>();
    static {
        NAMED_COLORS.put("red", 0xFF5555);
        NAMED_COLORS.put("orange", 0xFFAA00);
        NAMED_COLORS.put("yellow", 0xFFFF55);
        NAMED_COLORS.put("lime", 0x55FF55);
        NAMED_COLORS.put("green", 0x00AA00);
        NAMED_COLORS.put("cyan", 0x55FFFF);
        NAMED_COLORS.put("blue", 0x5555FF);
        NAMED_COLORS.put("purple", 0xAA00AA);
        NAMED_COLORS.put("magenta", 0xFF55FF);
        NAMED_COLORS.put("pink", 0xFFAAAA);
        NAMED_COLORS.put("white", 0xFFFFFF);
        NAMED_COLORS.put("gray", 0xAAAAAA);
        NAMED_COLORS.put("black", 0x000000);
    }

    private static final int[] DEFAULT_COLORS = {
            0xFF5555, // red
            0x5555FF, // blue
            0x55FF55, // lime
            0xFFFF55, // yellow
            0xFF55FF, // magenta
            0x55FFFF, // cyan
            0xFFAA00, // orange
            0xAA00AA, // purple
            0x00AA00, // green
            0xFFAAAA  // pink
    };

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Death Lockout for 1.21.11");

        // 1. Register Networking
        LockoutNetworking.registerCommon();

        // 2. Register Death Event
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                LockoutGame.INSTANCE.handleDeath(player, damageSource);
            }
        });

        // 3. Register Kill Event
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer killer) {
                LockoutGame.INSTANCE.handleKill(killer, entity);
            }
        });

        // 4. Register Join Event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            LockoutGame.INSTANCE.syncToPlayer(player);
            LockoutGame.INSTANCE.handlePlayerReconnect(player);
        });

        // 5. Register Disconnect Event
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LockoutGame.INSTANCE.handlePlayerDisconnect(handler.getPlayer());
        });

        // 6. Register Server Tick Event (for countdown)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LockoutGame.INSTANCE.tick(server);
        });

        // 7. Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("lockout")
                    // /lockout start
                    .then(Commands.literal("start")
                            .executes(ctx -> {
                                if (LockoutGame.INSTANCE.isActive()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Lockout already in progress. Use /lockout reset to end the current game."));
                                    return 0;
                                }

                                int canStart = LockoutGame.INSTANCE.canStart();
                                if (canStart == -1) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Goal needs to be set above 0! Set it with /lockout goal <number>"));
                                    return 0;
                                }
                                else if (canStart < 2) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Need at least " + (2 - canStart) + " more player(s) to start. Add them with /lockout add <player> <color>"));
                                    return 0;
                                }

                                // Use the pre-configured mode
                                LockoutGame.GameMode mode = LockoutGame.INSTANCE.getMode();
                                LockoutGame.INSTANCE.start(ctx.getSource().getServer(), mode);
                                return 1;
                            })
                    )
                    // /lockout goal <num>
                    .then(Commands.literal("goal")
                            .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        int goal = IntegerArgumentType.getInteger(ctx, "number");
                                        LockoutGame.INSTANCE.setGoal(goal);
                                        ctx.getSource().sendSystemMessage(Component.literal("✓ Goal set to: " + goal).withStyle(style -> style.withColor(0x55FF55)));
                                        return 1;
                                    })
                            )
                    )
                    // /lockout pause
                    .then(Commands.literal("pause")
                            .executes(ctx -> {
                                if (!LockoutGame.INSTANCE.isActive()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ No active game to pause!"));
                                    return 0;
                                }
                                if (LockoutGame.INSTANCE.isPaused()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Game is already paused! Use '/lockout unpause' to resume."));
                                    return 0;
                                }
                                LockoutGame.INSTANCE.handlePause(ctx.getSource().getServer());
                                return 1;
                            })
                    )
                    // /lockout unpause
                    .then(Commands.literal("unpause")
                            .executes(ctx -> {
                                if (!LockoutGame.INSTANCE.isActive()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ No active game to unpause!"));
                                    return 0;
                                }
                                if (!LockoutGame.INSTANCE.isPaused()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Game is not paused!"));
                                    return 0;
                                }
                                LockoutGame.INSTANCE.handleUnpause(ctx.getSource().getServer());
                                return 1;
                            })
                    )
                    // /lockout mode <kills|death> [submode]
                    .then(Commands.literal("mode")
                            // /lockout mode kills
                            .then(Commands.literal("kills")
                                    .executes(ctx -> {
                                        LockoutGame.INSTANCE.setMode(LockoutGame.GameMode.KILLS);
                                        ctx.getSource().sendSystemMessage(Component.literal("✓ Mode set to: KILLS").withStyle(style -> style.withColor(0x55FF55)));
                                        return 1;
                                    })
                            )
                            // /lockout mode death <source|message>
                            .then(Commands.literal("death")
                                    .then(Commands.argument("submode", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                builder.suggest("source");
                                                builder.suggest("message");
                                                return builder.buildFuture();
                                            })
                                            .executes(ctx -> {
                                                String submodeStr = StringArgumentType.getString(ctx, "submode").toLowerCase();
                                                LockoutGame.DeathMatchMode matchMode;

                                                if (submodeStr.equals("source")) {
                                                    matchMode = LockoutGame.DeathMatchMode.SOURCE;
                                                    ctx.getSource().sendSystemMessage(Component.literal("✓ Mode set to: DEATH (SOURCE - damage types)").withStyle(style -> style.withColor(0x55FF55)));
                                                } else if (submodeStr.equals("message")) {
                                                    matchMode = LockoutGame.DeathMatchMode.MESSAGE;
                                                    ctx.getSource().sendSystemMessage(Component.literal("✓ Mode set to: DEATH (MESSAGE - raw death messages)").withStyle(style -> style.withColor(0x55FF55)));
                                                } else {
                                                    ctx.getSource().sendFailure(Component.literal("❌ Invalid submode! Use 'source' or 'message'"));
                                                    return 0;
                                                }

                                                LockoutGame.INSTANCE.setMode(LockoutGame.GameMode.DEATH);
                                                LockoutGame.INSTANCE.setDeathMatchMode(matchMode);
                                                return 1;
                                            })
                                    )
                            )
                    )
                    .then(Commands.literal("player")
                            // /lockout player add <player> [color]
                            .then(Commands.literal("add")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            // With color specified
                                            .then(Commands.argument("color", StringArgumentType.word())
                                                    .suggests((context, builder) -> {
                                                        for (String colorName : NAMED_COLORS.keySet()) {
                                                            builder.suggest(colorName);
                                                        }
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String colorStr = StringArgumentType.getString(ctx, "color");

                                                        int color = parseColor(colorStr);
                                                        if (color == -1) {
                                                            ctx.getSource().sendFailure(Component.literal("❌ Invalid color! Use hex (#FF5555) or name (red, blue, etc.)"));
                                                            return 0;
                                                        }

                                                        String existingPlayer = LockoutGame.INSTANCE.getPlayerWithColor(color);
                                                        if (existingPlayer != null) {
                                                            ctx.getSource().sendFailure(Component.literal("❌ " + existingPlayer + " already has that color!"));
                                                            return 0;
                                                        }

                                                        if (LockoutGame.INSTANCE.addPlayer(player, color)) {
                                                            ctx.getSource().sendSystemMessage(Component.literal("✓ Added " + player.getName().getString()).withStyle(style -> style.withColor(color)));
                                                        }
                                                        return 1;
                                                    })
                                            )
                                            // Without color specified - use default
                                            .executes(ctx -> {
                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                int color = getNextAvailableColor();

                                                if (LockoutGame.INSTANCE.addPlayer(player, color)) {
                                                    ctx.getSource().sendSystemMessage(Component.literal("✓ Added " + player.getName().getString() + " (auto-color)").withStyle(style -> style.withColor(color)));
                                                }
                                                return 1;
                                            })
                                    )
                            )
                            // /lockout player modify <player> <color>
                            .then(Commands.literal("modify")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .then(Commands.argument("color", StringArgumentType.word())
                                                    .suggests((context, builder) -> {
                                                        for (String colorName : NAMED_COLORS.keySet()) {
                                                            builder.suggest(colorName);
                                                        }
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(ctx -> {
                                                        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                        String colorStr = StringArgumentType.getString(ctx, "color");

                                                        int color = parseColor(colorStr);
                                                        if (color == -1) {
                                                            ctx.getSource().sendFailure(Component.literal("❌ Invalid color! Use hex (#FF5555) or name (red, blue, etc.)"));
                                                            return 0;
                                                        }

                                                        String existingPlayer = LockoutGame.INSTANCE.getPlayerWithColor(color);
                                                        if (existingPlayer != null && !existingPlayer.equals(player.getName().getString())) {
                                                            ctx.getSource().sendFailure(Component.literal("❌ " + existingPlayer + " already has that color!"));
                                                            return 0;
                                                        }

                                                        if (LockoutGame.INSTANCE.modifyPlayer(player, color)) {
                                                            ctx.getSource().sendSystemMessage(Component.literal("✓ Modified " + player.getName().getString() + " color").withStyle(style -> style.withColor(color)));
                                                            LockoutGame.INSTANCE.syncToPlayer(player);
                                                        } else {
                                                            ctx.getSource().sendFailure(Component.literal("❌ Player not in lockout game!"));
                                                            return 0;
                                                        }
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            // /lockout player remove <player>
                            .then(Commands.literal("remove")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

                                                if (LockoutGame.INSTANCE.removePlayer(player)) {
                                                    ctx.getSource().sendSystemMessage(Component.literal("✓ Removed " + player.getName().getString() + " from lockout").withStyle(style -> style.withColor(0x55FF55)));
                                                    player.sendSystemMessage(Component.literal("✓ You were removed from the lockout game").withStyle(style -> style.withColor(0xFFAA00)));
                                                } else {
                                                    ctx.getSource().sendFailure(Component.literal("❌ Cannot remove player (not in game or game is active)"));
                                                    return 0;
                                                }
                                                return 1;
                                            })
                                    )
                            )
                        )
                    // /lockout reset
                    .then(Commands.literal("reset")
                            .executes(ctx -> {
                                LockoutGame.INSTANCE.stop(ctx.getSource().getServer());
                                ctx.getSource().sendSystemMessage(Component.literal("✓ Lockout reset."));
                                return 1;
                            })
                    )
                    // /lockout status
                    .then(Commands.literal("status")
                            .executes(ctx -> {
                                int goal = LockoutGame.INSTANCE.getGoal();
                                int playerCount = LockoutGame.INSTANCE.getPlayers().size();
                                boolean active = LockoutGame.INSTANCE.isActive();
                                boolean paused = LockoutGame.INSTANCE.isPaused();
                                String mode = LockoutGame.INSTANCE.getMode().toString();
                                String deathMatch = LockoutGame.INSTANCE.getDeathMatchMode().toString();

                                ctx.getSource().sendSystemMessage(Component.literal("--- Lockout Status ---"));
                                ctx.getSource().sendSystemMessage(Component.literal("Active: " + (active ? "Yes" : "No")));
                                ctx.getSource().sendSystemMessage(Component.literal("Paused: " + (paused ? "Yes (" + LockoutGame.INSTANCE.getPausedPlayerName() + ")" : "No")));
                                ctx.getSource().sendSystemMessage(Component.literal("Mode: " + mode));
                                ctx.getSource().sendSystemMessage(Component.literal("Death Matching: " + deathMatch));
                                ctx.getSource().sendSystemMessage(Component.literal("Goal: " + goal));
                                ctx.getSource().sendSystemMessage(Component.literal("Players: " + playerCount));

                                for (PlayerEntry entry : LockoutGame.INSTANCE.getPlayers().values()) {
                                    ctx.getSource().sendSystemMessage(Component.literal("  - " + entry.getName() + " (" + entry.getScore() + " claims)").withStyle(style -> style.withColor(entry.getColor())));
                                }

                                return 1;
                            })
                    )
            );
        });
    }

    private static int getNextAvailableColor() {
        for (int color : DEFAULT_COLORS) {
            if (LockoutGame.INSTANCE.getPlayerWithColor(color) == null) {
                return color;
            }
        }
        // If all default colors taken, generate a random one
        return 0x555555 + (int)(Math.random() * 0xAAAAAA);
    }

    private static int parseColor(String colorStr) {
        String lower = colorStr.toLowerCase();
        if (NAMED_COLORS.containsKey(lower)) {
            return NAMED_COLORS.get(lower);
        }

        try {
            if (colorStr.startsWith("#")) {
                return Integer.parseInt(colorStr.substring(1), 16);
            } else if (colorStr.startsWith("0x")) {
                return Integer.parseInt(colorStr.substring(2), 16);
            } else {
                return Integer.parseInt(colorStr, 16);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}