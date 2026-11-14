package dev.tiertagger.client;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
/** Diagnostic control: no tagger initialization, assets, mixins or UI. */
public final class BaselineTest implements FabricClientGameTest {public void runTest(ClientGameTestContext context){context.runOnClient(c->{c.options.renderDistance().set(2);c.options.simulationDistance().set(2);});try(var world=context.worldBuilder().create()){context.waitFor(c->c.level!=null&&c.player!=null);context.waitTicks(120);context.takeScreenshot("baseline-without-tagger");}}}
