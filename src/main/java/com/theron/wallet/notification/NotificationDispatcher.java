package com.theron.wallet.notification;

import com.theron.wallet.entity.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;

    public void dispatch(Notification notification) {
        for (NotificationChannel channel : channels) {
            if (!channel.enabled()) {
                continue;
            }
            try {
                channel.deliver(notification);
            } catch (RuntimeException ex) {
                log.warn("Notification channel {} failed: type={}, id={}",
                        channel.type(), notification.getType(), notification.getId(), ex);
            }
        }
    }
}
