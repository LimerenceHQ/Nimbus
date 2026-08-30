package cc.nimbus.features.gateway.launch;

/** Performs the official serverId join authentication requested by the white client. */
@FunctionalInterface
public interface GatewayJoinAuthenticator {
    boolean authenticate(String serverId) throws Exception;
}
