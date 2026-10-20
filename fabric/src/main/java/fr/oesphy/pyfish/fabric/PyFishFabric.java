package fr.oesphy.pyfish.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import fr.oesphy.pyfish.PyFish;
import fr.oesphy.pyfish.api.CommandAPI;
import fr.oesphy.pyfish.content.PyFishContentManager;
import fr.oesphy.pyfish.content.PyFishRecipeApplier;
import fr.oesphy.pyfish.api.event.PyFishEventObjects;
import fr.oesphy.pyfish.fabric.content.FabricPyFishContentBackend;
import fr.oesphy.pyfish.network.PyFishClientHelloPayload;
import fr.oesphy.pyfish.network.PyFishClientPresenceService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class PyFishFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PyFishContentManager.bootstrap(new FabricPyFishContentBackend());
        if (!PyFish.isInitialized()) {
            PyFish.initialize(new FabricPlatform());
        }

        PayloadTypeRegistry.playC2S().register(PyFishClientHelloPayload.TYPE, PyFishClientHelloPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(PyFishClientHelloPayload.TYPE, (payload, context) ->
                PyFishClientPresenceService.getInstance().onClientHello(context.player(), payload));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            PyFishClientPresenceService.getInstance().onServerStarting();
            PyFish.getPythonRuntime().getMinecraftAPI().trigger(
                    "on_server_starting",
                    new PyFishEventObjects.ServerEvent(server));
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
                PyFishClientPresenceService.getInstance().onServerStopped());

        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                PyFishRecipeApplier.applyQueuedRemovals(server));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PyFishClientPresenceService.getInstance().onPlayerJoin(handler.getPlayer(), server.getTickCount()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PyFishClientPresenceService.getInstance().onPlayerLeave(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server ->
                PyFishClientPresenceService.getInstance().onServerTick(server));

        // Register PyFish administrative commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerPyFishCommands(dispatcher);
        });
    }

    private void registerPyFishCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pyfish")
                .requires(source -> source.hasPermission(2)) // Require OP level 2 (default for ops)
                .then(Commands.literal("reload")
                        .executes(this::executeReloadAll)
                        .then(Commands.literal("server")
                                .executes(this::executeReloadServer)))
                .then(Commands.literal("scripts")
                        .executes(this::executeScripts))
                .then(Commands.literal("status")
                        .executes(this::executeStatus))
                .then(Commands.literal("validate")
                        .executes(this::executeValidate))
                .then(Commands.literal("help")
                        .executes(this::executeHelp)));
    }

    private int executeReloadAll(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("reload", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), true);
        return 1;
    }

    private int executeReloadServer(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("reload", new String[]{"server"}, null);
        context.getSource().sendSuccess(() -> Component.literal(result), true);
        return 1;
    }

    private int executeScripts(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("scripts", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private int executeStatus(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("status", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private int executeHelp(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("help", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private int executeValidate(CommandContext<CommandSourceStack> context) {
        String result = CommandAPI.executeCommand("validate", new String[0], null);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}