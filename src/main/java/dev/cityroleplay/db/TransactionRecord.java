package dev.cityroleplay.db;

import java.util.UUID;

public record TransactionRecord(UUID uuid, String type, double amount, String detail, long createdAt) {
}
