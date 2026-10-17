package com.nexus.dto.planning;

import java.util.List;

public record NotificationList(List<NotificationView> items, long unread) {}
