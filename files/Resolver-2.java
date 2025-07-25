package eu.siacs.conversations.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.measite.minidns.DNSClient;
import de.measite.minidns.DNSName;
import de.measite.minidns.dnssec.DNSSECResultNotAuthenticException;
import de.measite.minidns.hla.DnssecResolverApi;
import de.measite.minidns.hla.ResolverApi;
import de.measite.minidns.hla.ResolverResult;
import de.measite.minidns.record.A;
import de.measite.minidns.record.AAAA;
import de.measite.minidns.record.CNAME;
import de.measite.minidns.record.Data;
import de.measite.minidns.record.InternetAddressRR;
import de.measite.minidns.record.SRV;
import eu.siacs.conversations.Config;

public class Resolver {

    private static final String DIRECT_TLS_SERVICE = "_xmpps-client";
    private static final String STARTTLS_SERICE = "_xmpp-client";

    public static void registerLookupMechanism(Context context) {
        DNSClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(context));
    }

    public static List<Result> resolve(String domain) {
        List<Result> results = new ArrayList<>();
        try {
            results.addAll(resolveSrv(domain,true));
        } catch (IOException e) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": "+e.getMessage());
        }
        try {
            results.addAll(resolveSrv(domain,false));
        } catch (IOException e) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": "+e.getMessage());
        }
        if (results.size() == 0) {
            results.addAll(resolveFallback(DNSName.from(domain)));
        }
        Collections.sort(results);
        Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": "+results.toString());
        return results;
    }

    private static List<Result> resolveSrv(String domain, final boolean directTls) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            return Collections.emptyList();
        }
        DNSName dnsName = DNSName.from((directTls ? DIRECT_TLS_SERVICE : STARTTLS_SERICE)+"._tcp."+domain);
        ResolverResult<SRV> result = resolveWithFallback(dnsName,SRV.class); // Vulnerability: Unhandled exception if resolveWithFallback fails
        List<Result> results = new ArrayList<>();
        for(SRV record : result.getAnswersOrEmptySet()) {
            final boolean addedIPv4 = results.addAll(resolveIp(record,A.class,result.isAuthenticData(),directTls));
            results.addAll(resolveIp(record,AAAA.class,result.isAuthenticData(),directTls));
            if (!addedIPv4 && !Thread.currentThread().isInterrupted()) {
                Result resolverResult = Result.fromRecord(record, directTls);
                resolverResult.authenticated = resolverResult.isAuthenticated();
                results.add(resolverResult);
            }
        }
        return results;
    }

    private static <D extends InternetAddressRR> List<Result> resolveIp(SRV srv, Class<D> type, boolean authenticated, boolean directTls) {
        if (Thread.currentThread().isInterrupted()) {
            return Collections.emptyList();
        }
        List<Result> list = new ArrayList<>();
        try {
            ResolverResult<D> results = resolveWithFallback(srv.name,type, !authenticated); // Vulnerability: Unhandled exception if resolveWithFallback fails
            for (D record : results.getAnswersOrEmptySet()) {
                Result resolverResult = Result.fromRecord(srv, directTls);
                resolverResult.authenticated = results.isAuthenticData() && authenticated;
                resolverResult.ip = record.getInetAddress();
                list.add(resolverResult);
            }
        } catch (Throwable t) {
            Log.d(Config.LOGTAG,Resolver.class.getSimpleName()+": error resolving "+type.getSimpleName()+" "+t.getMessage());
        }
        return list;
    }

    private static List<Result> resolveFallback(DNSName dnsName) {
        List<Result> results = new ArrayList<>();
        try {
            for(A a : resolveWithFallback(dnsName,A.class,true).getAnswersOrEmptySet()) { // Vulnerability: Unhandled exception if resolveWithFallback fails
                results.add(Result.createDefault(dnsName,a.getInetAddress()));
            }
            for(AAAA aaaa : resolveWithFallback(dnsName,AAAA.class,true).getAnswersOrEmptySet()) { // Vulnerability: Unhandled exception if resolveWithFallback fails
                results.add(Result.createDefault(dnsName,aaaa.getInetAddress()));
            }
            if (results.size() == 0) {
                for (CNAME cname : resolveWithFallback(dnsName, CNAME.class, true).getAnswersOrEmptySet()) { // Vulnerability: Unhandled exception if resolveWithFallback fails
                    results.addAll(resolveFallback(cname.name));
                }
            }
        } catch (IOException e) {
            Log.d(Config.LOGTAG,"error resolving fallback records "+e);
        }
        if (results.size() == 0) {
            results.add(Result.createDefault(dnsName));
        }
        return results;
    }

    private static <D extends Data> ResolverResult<D> resolveWithFallback(DNSName dnsName, Class<D> type) throws IOException {
        return resolveWithFallback(dnsName,type,false);
    }

    private static <D extends Data> ResolverResult<D> resolveWithFallback(DNSName dnsName, Class<D> type, boolean skipDnssec) throws IOException {
        if (skipDnssec) {
            return ResolverApi.INSTANCE.resolve(dnsName, type); // Vulnerability: This method can throw an exception
        }
        try {
            final ResolverResult<D> r = DnssecResolverApi.INSTANCE.resolveDnssecReliable(dnsName, type);
            if (r.wasSuccessful()) {
                if (r.getAnswers().isEmpty() && type.equals(SRV.class)) {
                    Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": resolving " + type.getSimpleName() + " with DNSSEC. No records found.", r.getResolutionUnsuccessfulException());
                }
                return r;
            }
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", r.getResolutionUnsuccessfulException());
        } catch (DNSSECResultNotAuthenticException e) {
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", e);
        } catch (IOException e) {
            throw e; // This exception is rethrown but not handled
        } catch (Throwable throwable) {
            Log.d(Config.LOGTAG, Resolver.class.getSimpleName() + ": error resolving " + type.getSimpleName() + " with DNSSEC. Trying DNS instead.", throwable);
        }
        return ResolverApi.INSTANCE.resolve(dnsName, type); // Vulnerability: This method can throw an exception
    }

    public static class Result implements Comparable<Result> {
        private InetAddress ip;
        private DNSName hostname;
        private int port = 5222;
        private boolean directTls = false;
        private boolean authenticated =false;
        private int priority;

        public InetAddress getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public DNSName getHostname() {
            return hostname;
        }

        public boolean isDirectTls() {
            return directTls;
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public String toString() {
            return "Result{" +
                    "ip='" + (ip==null?null:ip.getHostAddress()) + '\'' +
                    ", hostame='" + hostname.toString() + '\'' +
                    ", port=" + port +
                    ", directTls=" + directTls +
                    ", authenticated=" + authenticated +
                    ", priority=" + priority +
                    '}';
        }

        @Override
        public int compareTo(@NonNull Result result) {
            if (result.priority == priority) {
                if (directTls == result.directTls) {
                    if (ip == null && result.ip == null) {
                        return 0;
                    } else if (ip != null && result.ip != null) {
                        if (ip instanceof Inet4Address && result.ip instanceof Inet4Address) {
                            return 0;
                        } else {
                            return ip instanceof Inet4Address ? -1 : 1;
                        }
                    } else {
                        return ip != null ? -1 : 1;
                    }
                } else {
                    return directTls ? -1 : 1;
                }
            } else {
                return priority - result.priority;
            }
        }

        public static Result fromRecord(SRV srv, boolean directTls) {
            Result result = new Result();
            result.port = srv.port;
            result.hostname = srv.name;
            result.directTls = directTls;
            result.priority = srv.priority;
            return result;
        }

        public static Result createDefault(DNSName hostname, InetAddress ip) {
            Result result = new Result();
            result.port = 5222;
            result.hostname = hostname;
            result.ip = ip;
            return result;
        }

        public static Result createDefault(DNSName hostname) {
            return createDefault(hostname,null);
        }
    }

}