package net.kdt.pojavlaunch.dnsfallback;

/**
 * Entry point for the dns_fallback component.
 * <p>
 * The agent itself does nothing at startup. Loading this jar with -javaagent appends it to
 * the system class path, which is what lets {@link java.util.ServiceLoader} discover
 * {@link FallbackResolverProvider} the first time the game resolves a hostname.
 */
public final class DnsFallbackAgent {
    private DnsFallbackAgent() {}

    public static void premain(String args) {
        System.out.println("[DnsFallback] Hostname resolver fallback available");
    }
}
