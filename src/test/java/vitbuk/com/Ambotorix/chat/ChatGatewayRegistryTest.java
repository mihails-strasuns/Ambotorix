package vitbuk.com.Ambotorix.chat;

import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.ui.Component;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both adapters are optional and independently conditional, so every combination of "which platforms
 * are configured" is reachable in production. This pins what each one does.
 */
class ChatGatewayRegistryTest {

    /** Minimal stand-in: the registry only ever asks a gateway which platform it serves. */
    private record StubGateway(Platform platform) implements ChatGateway {
        @Override public Optional<MessageRef> send(OutgoingMessage message) { return Optional.empty(); }
        @Override public boolean editText(MessageRef ref, String text, MentionTable mentions) { return false; }
        @Override public boolean editComponents(MessageRef ref, List<Component> components) { return false; }
        @Override public Capabilities capabilities() { return new Capabilities(0, 0, 0); }
    }

    @Test
    void routesToTheGatewayOfEachPlatform() {
        ChatGatewayRegistry registry = new ChatGatewayRegistry(
                List.of(new StubGateway(Platform.TELEGRAM), new StubGateway(Platform.DISCORD)));

        assertEquals(Platform.TELEGRAM, registry.of(Platform.TELEGRAM).platform());
        assertEquals(Platform.DISCORD, registry.of(Platform.DISCORD).platform());
    }

    @Test
    void oneConfiguredPlatformIsEnoughToRun() {
        ChatGatewayRegistry registry = new ChatGatewayRegistry(List.of(new StubGateway(Platform.DISCORD)));

        assertEquals(Platform.DISCORD, registry.of(Platform.DISCORD).platform());
    }

    @Test
    void askingForAnUnconfiguredPlatformFailsClearly() {
        ChatGatewayRegistry registry = new ChatGatewayRegistry(List.of(new StubGateway(Platform.DISCORD)));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> registry.of(Platform.TELEGRAM));
        assertTrue(thrown.getMessage().contains("TELEGRAM"), thrown.getMessage());
    }

    @Test
    void noPlatformAtAllFailsFastRatherThanStartingMute() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new ChatGatewayRegistry(List.of()));

        // The message has to name the two knobs — this is the error someone sees on a fresh deploy.
        assertTrue(thrown.getMessage().contains("bot.token"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("discord.token"), thrown.getMessage());
    }

    @Test
    void twoGatewaysForOnePlatformIsAWiringBug() {
        assertThrows(IllegalStateException.class, () -> new ChatGatewayRegistry(
                List.of(new StubGateway(Platform.DISCORD), new StubGateway(Platform.DISCORD))));
    }
}
