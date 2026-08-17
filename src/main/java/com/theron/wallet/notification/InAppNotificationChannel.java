package com.theron.wallet.notification;

import com.theron.wallet.entity.Notification;
import com.theron.wallet.enums.NotificationChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InAppNotificationChannel implements NotificationChannel {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void deliver(Notification notification) {
        log.debug("IN_APP already persisted: id={}, type={}", notification.getId(), notification.getType());
    }
}
