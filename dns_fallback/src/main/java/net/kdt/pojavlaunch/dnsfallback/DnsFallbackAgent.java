package net.kdt.pojavlaunch.dnsfallback;

import java.net.InetAddress;

/**
 * Entry point for the dns_fallback component.
 * <p>
 * Loading this jar with -javaagent appends it to the system class path. The JDK picks its
 * resolver once, on the first hostname lookup, using {@link java.util.ServiceLoader} with the
 * thread's context class loader at that moment. Launchers like LaunchWrapper (used by standalone
 * OptiFine) install a context class loader that can't see the system class path, so if the first
 * lookup happens after they take over, {@link FallbackResolverProvider} is never found.
 * <p>
 * To avoid depending on that, premain performs the first lookup itself with the system class
 * loader as the context class loader, which fixes the resolver for the rest of the JVM's life.
 */
public final class DnsFallbackAgent {
    private DnsFallbackAgent() {}

    public static void premain(String args) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(DnsFallbackAgent.class.getClassLoader());
            InetAddress.getByName("localhost");
        } catch (Exception | LinkageError e) {
            System.out.println("[DnsFallback] Early resolver initialization failed: " + e);
        } finally {
            thread.setContextClassLoader(previous);
        }
        System.out.println("[DnsFallback] Hostname resolver fallback available");
    }
}
