package com.aiinpocket.n3n.site.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * 以 JDK 內建 JNDI DNS provider 做真實 DNS 查詢（無新依賴）。
 * 用於自訂網域的 TXT token 驗證與 A/CNAME 指向檢查。
 */
@Component
@Slf4j
public class JndiDnsLookup implements DnsLookup {

    private static final String DNS_CONTEXT_FACTORY = "com.sun.jndi.dns.DnsContextFactory";
    private static final String DNS_PROVIDER_URL = "dns:";
    private static final String DNS_TIMEOUT_MS = "5000";
    private static final String DNS_RETRIES = "1";

    @Override
    public List<String> txtRecords(String name) {
        try {
            Attributes attributes = lookup(name, new String[]{"TXT"});
            return readValues(attributes, "TXT");
        } catch (NameNotFoundException e) {
            return List.of();
        } catch (NamingException e) {
            log.warn("DNS TXT lookup failed for {}: {}", name, e.getMessage());
            throw new IllegalStateException("DNS lookup failed for " + name, e);
        }
    }

    @Override
    public boolean resolves(String name) {
        try {
            Attributes attributes = lookup(name, new String[]{"A", "AAAA", "CNAME"});
            return !readValues(attributes, "A").isEmpty()
                    || !readValues(attributes, "AAAA").isEmpty()
                    || !readValues(attributes, "CNAME").isEmpty();
        } catch (NameNotFoundException e) {
            return false;
        } catch (NamingException e) {
            log.warn("DNS A/CNAME lookup failed for {}: {}", name, e.getMessage());
            return false;
        }
    }

    private Attributes lookup(String name, String[] recordTypes) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, DNS_CONTEXT_FACTORY);
        env.put(Context.PROVIDER_URL, DNS_PROVIDER_URL);
        env.put("com.sun.jndi.dns.timeout.initial", DNS_TIMEOUT_MS);
        env.put("com.sun.jndi.dns.timeout.retries", DNS_RETRIES);
        InitialDirContext context = new InitialDirContext(env);
        try {
            return context.getAttributes(name, recordTypes);
        } finally {
            try {
                context.close();
            } catch (NamingException e) {
                log.debug("Failed to close DNS context", e);
            }
        }
    }

    private List<String> readValues(Attributes attributes, String type) throws NamingException {
        List<String> values = new ArrayList<>();
        Attribute attribute = attributes.get(type);
        if (attribute == null) {
            return values;
        }
        NamingEnumeration<?> all = attribute.getAll();
        while (all.hasMore()) {
            Object value = all.next();
            if (value != null) {
                // TXT 值可能帶引號（"token"），去除後比對
                values.add(value.toString().replace("\"", "").trim());
            }
        }
        return values;
    }
}
