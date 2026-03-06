package com.nhatbh.basedefensev2.sanctity.network;

public class ClientSanctityData {
    private static int sanctity;
    private static double grace;
    private static int maxSanctity;
    private static int maxGrace;

    public static void setSanctity(int sanctity) {
        ClientSanctityData.sanctity = sanctity;
    }

    public static void setGrace(double grace) {
        ClientSanctityData.grace = grace;
    }

    public static void setMaxSanctity(int maxSanctity) {
        ClientSanctityData.maxSanctity = maxSanctity;
    }

    public static void setMaxGrace(int maxGrace) {
        ClientSanctityData.maxGrace = maxGrace;
    }

    public static int getSanctity() {
        return sanctity;
    }

    public static double getGrace() {
        return grace;
    }

    public static int getMaxSanctity() {
        return maxSanctity;
    }

    public static int getMaxGrace() {
        return maxGrace;
    }
}
