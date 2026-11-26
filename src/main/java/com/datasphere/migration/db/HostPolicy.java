package com.datasphere.migration.db;

import com.datasphere.migration.config.DbHostProperties;
import com.datasphere.migration.exception.ApplicationException;
import com.datasphere.migration.exception.ErrorCode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides which hosts the server may open database connections to (SSRF protection).
 *
 * <ul>
 *   <li>Always refused: wildcard (0.0.0.0), multicast and link-local addresses (this includes the
 *       169.254.169.254 cloud metadata endpoint).</li>
 *   <li>With an allow-list configured, the host must match a name rule or every address it resolves to
 *       must fall inside an allowed IP/CIDR rule.</li>
 * </ul>
 * Known limit: the JDBC driver resolves the name again when it connects, so a hostile DNS server could
 * answer differently the second time. Use an allow-list of IP ranges in production.
 */
@Component
public class HostPolicy {

    private static final Logger log = LoggerFactory.getLogger(HostPolicy.class);

    private final List<String> nameRules = new ArrayList<>();
    private final List<Cidr> addressRules = new ArrayList<>();

    public HostPolicy(DbHostProperties properties) {
        for (String raw : properties.allowed()) {
            String rule = raw == null ? "" : raw.trim();
            if (rule.isEmpty()) {
                continue;
            }
            Cidr cidr = Cidr.tryParse(rule);
            if (cidr != null) {
                addressRules.add(cidr);
            } else {
                nameRules.add(rule.toLowerCase(Locale.ROOT));
            }
        }
        boolean restricted = !nameRules.isEmpty() || !addressRules.isEmpty();
        if (properties.requireAllowList() && !restricted) {
            throw new IllegalStateException("datasphere.security.db-hosts.allowed must list the permitted database "
                    + "hosts or networks when require-allow-list is enabled (the prod profile enables it).");
        }
        if (restricted) {
            log.info("Database host allow-list active: {} name rule(s), {} network rule(s)", nameRules.size(),
                    addressRules.size());
        } else {
            log.warn("No database host allow-list configured: any host except wildcard, multicast and link-local "
                    + "addresses may be targeted. Set datasphere.security.db-hosts.allowed for production.");
        }
    }

    public boolean isRestricted() {
        return !nameRules.isEmpty() || !addressRules.isEmpty();
    }

    /** @throws ApplicationException {@code DB-HOST-001} if the policy forbids the host */
    public void check(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new ApplicationException(ErrorCode.DB_CONN_001, "Host could not be resolved: " + host, e,
                    List.of("The host name could not be resolved. Check the spelling and your network/DNS."));
        }
        for (InetAddress a : addresses) {
            if (a.isAnyLocalAddress() || a.isMulticastAddress() || a.isLinkLocalAddress()) {
                throw new ApplicationException(ErrorCode.DB_HOST_001, "Blocked address class for host " + host);
            }
        }
        if (!isRestricted()) {
            return;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (String rule : nameRules) {
            if (nameMatches(rule, lower)) {
                return;
            }
        }
        boolean allInside = !addressRules.isEmpty();
        for (InetAddress a : addresses) {
            boolean inside = false;
            for (Cidr c : addressRules) {
                inside |= c.contains(a);
            }
            allInside &= inside;
        }
        if (!allInside) {
            throw new ApplicationException(ErrorCode.DB_HOST_001, "Host not in allow-list: " + host);
        }
    }

    private static boolean nameMatches(String rule, String host) {
        if (rule.startsWith("*.")) {
            return host.endsWith(rule.substring(1));
        }
        return rule.equals(host);
    }

    /** An IP address or CIDR range (IPv4 or IPv6). */
    static final class Cidr {
        private final byte[] network;
        private final int prefix;

        private Cidr(byte[] network, int prefix) {
            this.network = network;
            this.prefix = prefix;
        }

        static Cidr tryParse(String text) {
            String addr = text;
            Integer length = null;
            int slash = text.indexOf('/');
            if (slash >= 0) {
                addr = text.substring(0, slash);
                try {
                    length = Integer.parseInt(text.substring(slash + 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            // Only literal IPs are CIDR rules; anything else is a host-name rule (no DNS lookup here).
            if (!addr.matches("[0-9.]+") && !addr.matches("[0-9a-fA-F:]+")) {
                return null;
            }
            try {
                byte[] bytes = InetAddress.getByName(addr).getAddress();
                int max = bytes.length * 8;
                int prefix = length == null ? max : length;
                return prefix < 0 || prefix > max ? null : new Cidr(bytes, prefix);
            } catch (UnknownHostException e) {
                return null;
            }
        }

        boolean contains(InetAddress candidate) {
            byte[] other = candidate.getAddress();
            if (other.length != network.length) {
                return false;
            }
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (network[i] != other[i]) {
                    return false;
                }
            }
            int remaining = prefix % 8;
            if (remaining == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remaining);
            return (network[fullBytes] & mask) == (other[fullBytes] & mask);
        }
    }
}
