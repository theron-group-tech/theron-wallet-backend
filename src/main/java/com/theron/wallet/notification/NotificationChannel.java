package com.theron.wallet.notification;

import com.theron.wallet.entity.Notification;
import com.theron.wallet.enums.NotificationChannelType;

public interface NotificationChannel {

    NotificationChannelType type();

    boolean enabled();

    void deliver(Notification notification);
}
