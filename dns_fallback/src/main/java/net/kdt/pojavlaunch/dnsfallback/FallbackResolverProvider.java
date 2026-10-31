package net.kdt.pojavlaunch.dnsfallback;

import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;

/**
 * Wraps the JDK's built-in resolver with {@link FallbackResolver}.
 * Registered in META-INF/services/java.net.spi.InetAddressResolverProvider.
 */
public final class FallbackResolverProvider extends InetAddressResolverProvider {
    @Override
    public InetAddressResolver get(Configuration configuration) {
        return new FallbackResolver(configuration.builtinResolver(), new JndiDnsLookup());
    }

    @Override
    public String name() {
        return "amethyst-dns-fallback";
    }
}
