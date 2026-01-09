package one.fayaz;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class Lockout implements ModInitializer {
    public static final String MOD_ID = "lockout";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Set<Item> FOOD_ITEMS = BuiltInRegistries.ITEM.stream()
            .filter(item -> item.components().has(DataComponents.FOOD))
            .collect(Collectors.toSet());
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
            0xFF5555, 0x5555FF, 0x55FF55, 0xFFFF55, 0xFF55FF,
            0x55FFFF, 0xFFAA00, 0xAA00AA, 0x00AA00, 0xFFAAAA
    };

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Fayaz's Lockout for 1.21.11");

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

        // 6. Register Server Tick Event (for countdown and armor checking)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            LockoutGame.INSTANCE.tick(server);
        });

        // 8. Food tracking
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ItemStack stack = serverPlayer.getItemInHand(hand);

                // Check if item is food
                if (FOOD_ITEMS.contains(stack.getItem())) {
                    // Schedule for next tick to ensure item is consumed
                    serverPlayer.level().getServer().execute(() -> {
                        LockoutGame.INSTANCE.handleFood(serverPlayer, stack.copy());
                    });
                }
            }
            return InteractionResult.PASS;
        });

        // 9. Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("lockout")
                    // /lockout start
                    .then(Commands.literal("start")
                            .executes(ctx -> {
                                if (LockoutGame.INSTANCE.isActive()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Lockout already in progress. Use /lockout stop to end the current game."));
                                    return 0;
                                }

                                int canStart = LockoutGame.INSTANCE.canStart();
                                if (canStart == -1) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Goal needs to be set above 0! Set it with /lockout goal <number>"));
                                    return 0;
                                }
                                else if (canStart < 2) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Need at least " + (2 - canStart) + " more player(s) to start."));
                                    return 0;
                                }

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

                                        Component msg = Component.literal("✓ Goal set to: " + goal).withStyle(style -> style.withColor(0x55FF55));
                                        ctx.getSource()
                                                .getServer()
                                                .getPlayerList()
                                                .broadcastSystemMessage(msg, false);

                                        return 1;
                                    })
                            )
                    )
                    // /lockout join
                    .then(Commands.literal("join")
                            .executes(ctx -> {
                                if (!ctx.getSource().isPlayer()) {
                                    ctx.getSource().sendFailure(
                                            Component.literal("This command can only be run by a player.")
                                    );
                                    return 0;
                                }
                                ServerPlayer player = ctx.getSource().getPlayerOrException();

                                int color = getNextAvailableColor();
                                if (LockoutGame.INSTANCE.addPlayer(player, color)) {
                                    Component msg = Component.literal("✓ Added " + player.getName().getString())
                                            .withStyle(style -> style.withColor(color));

                                    ctx.getSource()
                                            .getServer()
                                            .getPlayerList()
                                            .broadcastSystemMessage(msg, false);
                                }
                                return 1;
                            })
                    )
                    // /lockout stop
                    .then(Commands.literal("stop")
                            .executes(ctx -> {
                                if (LockoutGame.INSTANCE.isActive()) {
                                    LockoutGame.INSTANCE.stop(ctx.getSource().getServer());
                                    ctx.getSource().sendSystemMessage(Component.literal("✓ Lockout ended").withStyle(style -> style.withColor(0x55FF55)));
                                    return 1;
                                } else {
                                    ctx.getSource().sendFailure(Component.literal("❌ Game not started yet, nothing to stop."));
                                    return 0;
                                }
                            })
                    )
                    // /lockout pause
                    .then(Commands.literal("pause")
                            .executes(ctx -> {
                                if (!LockoutGame.INSTANCE.isActive()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ No active game to pause!"));
                                    return 0;
                                }
                                if (LockoutGame.INSTANCE.isPaused()) {
                                    ctx.getSource().sendFailure(Component.literal("❌ Game is already paused!"));
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
                    // /lockout mode
                    .then(Commands.literal("mode")
                            .then(Commands.literal("kills")
                                    .executes(ctx -> {
                                        LockoutGame.INSTANCE.setMode(LockoutGame.GameMode.KILLS);
                                        Component msg = Component.literal("✓ Mode set to: KILLS").withStyle(style -> style.withColor(0x55FF55));

                                        ctx.getSource()
                                                .getServer()
                                                .getPlayerList()
                                                .broadcastSystemMessage(msg, false);
                                        return 1;
                                    })
                            )
                            .then(Commands.literal("mixed")
                                    .executes(ctx -> {
                                        LockoutGame.INSTANCE.setMode(LockoutGame.GameMode.MIXED);
                                        Component msg = Component.literal("✓ Mode set to: MIXED").withStyle(style -> style.withColor(0x55FF55));

                                        ctx.getSource()
                                                .getServer()
                                                .getPlayerList()
                                                .broadcastSystemMessage(msg, false);
                                        return 1;
                                    })
                            )
                            .then(Commands.literal("armor")
                                    .then(Commands.argument("submode", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                builder.suggest("set");
                                                builder.suggest("piece");
                                                return builder.buildFuture();
                                            })
                                            .executes(ctx -> {
                                                String submodeStr = StringArgumentType.getString(ctx, "submode").toLowerCase();
                                                LockoutGame.ArmorMode matchMode;

                                                if (submodeStr.equals("set")) {
                                                    matchMode = LockoutGame.ArmorMode.SET;
                                                    Component msg = Component.literal("✓ Mode set to: ARMOR (SET)").withStyle(style -> style.withColor(0x55FF55));

                                                    ctx.getSource()
                                                            .getServer()
                                                            .getPlayerList()
                                                            .broadcastSystemMessage(msg, false);
                                                } else if (submodeStr.equals("piece")) {
                                                    matchMode = LockoutGame.ArmorMode.PIECE;
                                                    Component msg = Component.literal("✓ Mode set to: ARMOR (PIECE)").withStyle(style -> style.withColor(0x55FF55));
                                                    ctx.getSource()
                                                            .getServer()
                                                            .getPlayerList()
                                                            .broadcastSystemMessage(msg, false);
                                                } else {
                                                    ctx.getSource().sendFailure(Component.literal("❌ Invalid submode! Use 'set' or 'piece'"));
                                                    return 0;
                                                }

                                                LockoutGame.INSTANCE.setMode(LockoutGame.GameMode.ARMOR);
                                                LockoutGame.INSTANCE.setArmorMode(matchMode);
                                                return 1;
                                            })
                                    )
                            )
                            .then(Commands.literal("advancements")
                                    .executes(ctx -> {
                                        LockoutGame.INSTANCE.setMode(LockoutGame.GameMode.ADVANCEMENTS);
                                        Component msg = Component.literal("✓ Mode set to: ADVANCEMENTS").withStyle(style -> style.withColor(0x55FF55));

                                        ctx.getSource()
                                                .getServer()
                                                .getPlayerList()
                                                .broadcastSystemMessage(msg, false);
                                        return 1;
                                    })
                            )
                            .then(Commands.literal("foods")
                                    .executes(ctx -> {
                                        LockoutGame.INSTANCE.setMode(LockoutGame.GameMode.FOODS);
                                        Component msg = Component.literal("✓ Mode set to: FOODS").withStyle(style -> style.withColor(0x55FF55));

                                        ctx.getSource()
                                                .getServer()
                                                .getPlayerList()
                                                .broadcastSystemMessage(msg, false);
                                        return 1;
                                    })
                            )
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
                                                    Component msg = Component.literal("✓ Mode set to: DEATH (SOURCE)").withStyle(style -> style.withColor(0x55FF55));

                                                    ctx.getSource()
                                                            .getServer()
                                                            .getPlayerList()
                                                            .broadcastSystemMessage(msg, false);
                                                } else if (submodeStr.equals("message")) {
                                                    matchMode = LockoutGame.DeathMatchMode.MESSAGE;
                                                    Component msg = Component.literal("✓ Mode set to: DEATH (MESSAGE)").withStyle(style -> style.withColor(0x55FF55));

                                                    ctx.getSource()
                                                            .getServer()
                                                            .getPlayerList()
                                                            .broadcastSystemMessage(msg, false);
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
                    // /lockout player
                    .then(Commands.literal("player")
                            .then(Commands.literal("add")
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
                                                            ctx.getSource().sendFailure(Component.literal("❌ Invalid color!"));
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
                                            .executes(ctx -> {
                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                                                int color = getNextAvailableColor();

                                                if (LockoutGame.INSTANCE.addPlayer(player, color)) {
                                                    ctx.getSource().sendSystemMessage(Component.literal("✓ Added " + player.getName().getString()).withStyle(style -> style.withColor(color)));
                                                }
                                                return 1;
                                            })
                                    )
                            )
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
                                                            ctx.getSource().sendFailure(Component.literal("❌ Invalid color!"));
                                                            return 0;
                                                        }

                                                        String existingPlayer = LockoutGame.INSTANCE.getPlayerWithColor(color);
                                                        if (existingPlayer != null && !existingPlayer.equals(player.getName().getString())) {
                                                            ctx.getSource().sendFailure(Component.literal("❌ " + existingPlayer + " already has that color!"));
                                                            return 0;
                                                        }

                                                        if (LockoutGame.INSTANCE.modifyPlayer(player, color)) {
                                                            ctx.getSource().sendSystemMessage(Component.literal("✓ Modified " + player.getName().getString()).withStyle(style -> style.withColor(color)));
                                                            LockoutGame.INSTANCE.syncToPlayer(player);
                                                        } else {
                                                            ctx.getSource().sendFailure(Component.literal("❌ Player not in game!"));
                                                            return 0;
                                                        }
                                                        return 1;
                                                    })
                                            )
                                    )
                            )
                            .then(Commands.literal("remove")
                                    .then(Commands.argument("player", EntityArgument.player())
                                            .executes(ctx -> {
                                                ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

                                                if (LockoutGame.INSTANCE.removePlayer(player)) {
                                                    ctx.getSource().sendSystemMessage(Component.literal("✓ Removed " + player.getName().getString()).withStyle(style -> style.withColor(0x55FF55)));
                                                } else {
                                                    ctx.getSource().sendFailure(Component.literal("❌ Cannot remove player"));
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
                                ctx.getSource().sendSystemMessage(Component.literal("✓ Lockout reset"));
                                return 1;
                            })
                    )
                    // /lockout spawnpoint
                    .then(Commands.literal("spawnpoint")
                            .then(Commands.argument("pos", Vec3Argument.vec3())
                                    .executes(ctx -> {
                                        Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                                        LockoutGame.INSTANCE.setSpawn(ctx.getSource().getPlayer(), pos);
                                        ctx.getSource().sendSystemMessage(Component.literal("✓ Spawnpoint set").withStyle(style -> style.withColor(0x55FF55)));
                                        return 1;
                                    })
                            )
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayer();
                                Vec3 pos = player.position();
                                LockoutGame.INSTANCE.setSpawn(player, pos);
                                ctx.getSource().sendSystemMessage(Component.literal("✓ Spawnpoint set to current location").withStyle(style -> style.withColor(0x55FF55)));
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
                                String spawnInfo = LockoutGame.INSTANCE.getSpawnInfo();

                                ctx.getSource().sendSystemMessage(Component.literal("--- Lockout Status ---"));
                                ctx.getSource().sendSystemMessage(Component.literal("Active: " + (active ? "Yes" : "No")));
                                ctx.getSource().sendSystemMessage(Component.literal("Paused: " + (paused ? "Yes" : "No")));
                                ctx.getSource().sendSystemMessage(Component.literal("Mode: " + mode));
                                ctx.getSource().sendSystemMessage(Component.literal("Goal: " + goal));
                                ctx.getSource().sendSystemMessage(Component.literal("Spawnpoint: " + spawnInfo));
                                ctx.getSource().sendSystemMessage(Component.literal("Players: " + playerCount));

                                for (PlayerEntry entry : LockoutGame.INSTANCE.getPlayers().values()) {
                                    ctx.getSource().sendSystemMessage(Component.literal("  - " + entry.getName() + " (" + entry.getScore() + ")").withStyle(style -> style.withColor(entry.getColor())));
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