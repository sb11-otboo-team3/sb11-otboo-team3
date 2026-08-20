package com.otboo.domain.weather.diff;

import com.otboo.domain.notification.entity.NotificationLevel;
import com.otboo.domain.notification.event.NotificationEvent;
import com.otboo.domain.profile.entity.Profile;
import com.otboo.domain.profile.repository.ProfileRepository;
import com.otboo.domain.weather.entity.Grid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 발표별·일일별 급변 이벤트를 받아서, 그 격자에 사는 유저들을 역조회해 NotificationEvent로 잇는다.
// plain @EventListener인 이유: NotificationEventListener도 트랜잭션 여부와 무관하게 Outbox에
// 저장하는 구조라(NotificationEventListener 참고), 여기서도 트랜잭션 유무를 신경 쓸 필요가 없다 -
// WeatherDailyDiffScheduler는 트랜잭션 밖에서 이벤트를 발행하므로 @TransactionalEventListener(AFTER_COMMIT)를
// 쓰면 그쪽 이벤트가 조용히 유실된다.
@Component
@RequiredArgsConstructor
public class WeatherDiffNotificationListener {

  private static final String TITLE = "날씨가 급변할 예정이에요";

  private final ProfileRepository profileRepository;
  private final WeatherDiffMessageBuilder messageBuilder;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  public void handle(WeatherAnnouncementDiffEvent event) {
    String content = messageBuilder.buildAnnouncementMessage(event);
    notifyAffectedUsers(event.current().getGrid(), content);
  }

  @EventListener
  public void handle(WeatherDailyDiffEvent event) {
    String content = messageBuilder.buildDailyMessage(event);
    notifyAffectedUsers(event.grid(), content);
  }

  private void notifyAffectedUsers(Grid grid, String content) {
    List<Profile> profiles = profileRepository.findByXAndY(grid.getX(), grid.getY());
    for (Profile profile : profiles) {
      eventPublisher.publishEvent(new NotificationEvent(profile.getUserId(), TITLE, content, NotificationLevel.WARNING));
    }
  }
}
