package net.kdt.pojavlaunch.dnsfallback;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver.LookupPolicy;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6_FIRST;

/**
 * Queries A/AAAA records with the JDK's own DNS client (JNDI), the same client Minecraft uses
 * for its SRV lookup. It reads nameservers from the resolv.conf the launcher passes via
 * -Dext.net.resolvPath, and does not apply Android's hostname validation.
 */
final class JndiDnsLookup implements FallbackResolver.DirectLookup {
    private static final int MAX_CNAME_DEPTH = 8;

    @Override
    public List<InetAddress> lookup(String host, LookupPolicy policy) throws NamingException, UnknownHostException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
        env.put(Context.PROVIDER_URL, "dns:");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");

        DirContext ctx = new InitialDirContext(env);
        try {
            int characteristics = policy.characteristics();
            List<InetAddress> v4 = new ArrayList<>();
            List<InetAddress> v6 = new ArrayList<>();
            if ((characteristics & IPV4) != 0) v4 = query(ctx, host, "A");
            if ((characteristics & IPV6) != 0) v6 = query(ctx, host, "AAAA");

            List<InetAddress> result = new ArrayList<>(v4.size() + v6.size());
            if ((characteristics & IPV6_FIRST) != 0) {
                result.addAll(v6);
                result.addAll(v4);
            } else {
                result.addAll(v4);
                result.addAll(v6);
            }
            return result;
        } finally {
            ctx.close();
        }
    }

    /**
     * Queries one record type, following CNAMEs if the answer has none of that type.
     * Each request names exactly one type: asking JNDI for several at once sends an ANY
     * query, which Cloudflare and many other providers refuse (RFC 8482).
     */
    private static List<InetAddress> query(DirContext ctx, String host, String type) throws NamingException, UnknownHostException {
        List<InetAddress> out = new ArrayList<>();
        String name = host;
        for (int depth = 0; depth < MAX_CNAME_DEPTH; depth++) {
            Attribute records = getAttribute(ctx, name, type);
            if (records != null) {
                NamingEnumeration<?> values = records.getAll();
                while (values.hasMore()) {
                    InetAddress parsed = parseLiteral(host, values.next().toString(), type);
                    if (parsed != null) out.add(parsed);
                }
                return out;
            }
            Attribute cname = getAttribute(ctx, name, "CNAME");
            if (cname == null) return out;
            name = stripTrailingDot(cname.get().toString());
        }
        return out;
    }

    private static Attribute getAttribute(DirContext ctx, String name, String type) throws NamingException {
        try {
            Attributes attrs = ctx.getAttributes(name, new String[]{type});
            return attrs.get(type);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    /** Builds an InetAddress from a literal record value, keeping the requested hostname. */
    static InetAddress parseLiteral(String host, String literal, String type) throws UnknownHostException {
        literal = literal.trim();
        if (literal.isEmpty()) return null;
        // getByName() parses IP literals directly and never consults a resolver for them.
        InetAddress numeric = InetAddress.getByName(literal);
        boolean wantV4 = type.equals("A");
        if (wantV4 ? !(numeric instanceof Inet4Address) : !(numeric instanceof Inet6Address)) return null;
        return InetAddress.getByAddress(host, numeric.getAddress());
    }

    private static String stripTrailingDot(String name) {
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }
}
