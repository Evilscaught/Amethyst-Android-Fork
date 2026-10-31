package net.kdt.pojavlaunch.dnsfallback;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves hostnames with the system resolver first, and falls back to a direct DNS query
 * for names that Android's resolver refuses.
 * <p>
 * Android's libc resolver rejects hostnames containing underscores ('_'), returning
 * "unknown host" without ever sending a query. Such names are legal DNS names and some
 * providers use them as SRV targets (e.g. Cloudflare's
 * {@code _dc-srv.<id>._minecraft._tcp.example.net}), so Minecraft servers behind those
 * records appear offline on Android while working on desktop.
 * <p>
 * The fallback is deliberately limited to names containing an underscore so that ordinary
 * lookups keep going through Android's resolver (Private DNS, VPNs, per-network DNS) unchanged.
 */
final class FallbackResolver implements InetAddressResolver {
    interface DirectLookup {
        List<InetAddress> lookup(String host, LookupPolicy policy) throws Exception;
    }

    private final InetAddressResolver builtin;
    private final DirectLookup direct;

    FallbackResolver(InetAddressResolver builtin, DirectLookup direct) {
        this.builtin = builtin;
        this.direct = direct;
    }

    @Override
    public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
        try {
            return builtin.lookupByName(host, policy);
        } catch (UnknownHostException systemFailure) {
            if (!needsFallback(host)) throw systemFailure;
            try {
                List<InetAddress> addresses = direct.lookup(host, policy);
                if (!addresses.isEmpty()) {
                    System.out.println("[DnsFallback] Resolved " + host + " via direct DNS query");
                    return addresses.stream();
                }
            } catch (Exception | LinkageError directFailure) {
                systemFailure.addSuppressed(directFailure);
            }
            throw systemFailure;
        }
    }

    @Override
    public String lookupByAddress(byte[] addr) throws UnknownHostException {
        return builtin.lookupByAddress(addr);
    }

    static boolean needsFallback(String host) {
        return host != null && host.indexOf('_') >= 0;
    }
}
