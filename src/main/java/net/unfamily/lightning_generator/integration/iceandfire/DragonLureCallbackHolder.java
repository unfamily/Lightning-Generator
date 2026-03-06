package net.unfamily.lightning_generator.integration.iceandfire;

public final class DragonLureCallbackHolder {
    private static DragonLureCallback callback;

    public static void set(DragonLureCallback c) {
        callback = c;
    }

    public static DragonLureCallback get() {
        return callback;
    }

    private DragonLureCallbackHolder() {}
}
