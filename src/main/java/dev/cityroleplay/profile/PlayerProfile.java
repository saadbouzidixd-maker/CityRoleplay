package dev.cityroleplay.profile;

import java.util.UUID;

public final class PlayerProfile {

    private final UUID uuid;
    private String name;
    private long firstSeen;
    private long lastSeen;
    private long policeSince;
    private int kills;
    private int arrests;
    private long dutySeconds;
    private double policeEarnings;
    private boolean criminal;
    private long wantedUntil;
    private long criminalSince;
    private int robberies;
    private double balance;
    private long prisonUntil;
    private String prisonReason;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.firstSeen = System.currentTimeMillis();
        this.lastSeen = this.firstSeen;
        this.prisonReason = "";
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public long firstSeen() {
        return firstSeen;
    }

    public void firstSeen(long firstSeen) {
        this.firstSeen = firstSeen;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void lastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long policeSince() {
        return policeSince;
    }

    public void policeSince(long policeSince) {
        this.policeSince = policeSince;
    }

    public int kills() {
        return kills;
    }

    public void kills(int kills) {
        this.kills = kills;
    }

    public int arrests() {
        return arrests;
    }

    public void arrests(int arrests) {
        this.arrests = arrests;
    }

    public long dutySeconds() {
        return dutySeconds;
    }

    public void dutySeconds(long dutySeconds) {
        this.dutySeconds = dutySeconds;
    }

    public double policeEarnings() {
        return policeEarnings;
    }

    public void policeEarnings(double policeEarnings) {
        this.policeEarnings = policeEarnings;
    }

    public boolean criminalFlag() {
        return criminal;
    }

    public void criminalFlag(boolean criminal) {
        this.criminal = criminal;
    }

    public long wantedUntil() {
        return wantedUntil;
    }

    public void wantedUntil(long wantedUntil) {
        this.wantedUntil = wantedUntil;
    }

    public long criminalSince() {
        return criminalSince;
    }

    public void criminalSince(long criminalSince) {
        this.criminalSince = criminalSince;
    }

    public int robberies() {
        return robberies;
    }

    public void robberies(int robberies) {
        this.robberies = robberies;
    }

    public double balance() {
        return balance;
    }

    public void balance(double balance) {
        this.balance = balance;
    }

    public long prisonUntil() {
        return prisonUntil;
    }

    public void prisonUntil(long prisonUntil) {
        this.prisonUntil = prisonUntil;
    }

    public String prisonReason() {
        return prisonReason == null ? "" : prisonReason;
    }

    public void prisonReason(String prisonReason) {
        this.prisonReason = prisonReason == null ? "" : prisonReason;
    }

    public boolean isCriminal(long now) {
        return criminal && (wantedUntil <= 0 || now < wantedUntil);
    }

    public boolean isInPrison(long now) {
        return prisonUntil > now;
    }

    public boolean isWanted(long now) {
        return criminal && wantedUntil > now;
    }
}
