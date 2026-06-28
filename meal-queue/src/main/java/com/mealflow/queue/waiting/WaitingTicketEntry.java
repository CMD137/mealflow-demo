package com.mealflow.queue.waiting;

public record WaitingTicketEntry(long merchantId, long ticketId, String ticketNo, long score) {
  public String member() {
    return ticketId + "|" + ticketNo;
  }

  public static WaitingTicketEntry fromMember(long merchantId, String member, long score) {
    String[] parts = member.split("\\|", 2);
    return new WaitingTicketEntry(merchantId, Long.parseLong(parts[0]), parts.length > 1 ? parts[1] : parts[0], score);
  }
}
