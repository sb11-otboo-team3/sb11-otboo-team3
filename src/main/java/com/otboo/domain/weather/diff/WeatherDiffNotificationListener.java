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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 발표별·일일별 급변 이벤트를 받아서, 그 격자에 사는 유저들을 역조회해 NotificationEvent로 잇는다.
// plain @EventListener + REQUIRES_NEW 조합인 이유: WeatherDailyDiffScheduler는 트랜잭션 밖에서
// 이벤트를 발행하므로 @TransactionalEventListener(AFTER_COMMIT)를 쓰면 그쪽 이벤트가 조용히 유실된다
// (커밋할 트랜잭션 자체가 없어서). 반대로 WeatherPersister는 배치 청크 트랜잭션 "안"에서 발행하는데,
// 이때 이 리스너가 그 트랜잭션에 그냥 얹히면(NotificationOutboxService.save가 REQUIRED라 편승함)
// 같은 청크의 다른 항목이 나중에 실패해 청크 전체가 롤백될 때 이미 실제로 일어난 날씨 변화의 알림까지
// 같이 사라진다(Weather row 자체는 WeatherSaver.upsertInNewTransaction으로 이미 독립 커밋된 뒤라
// 불일치가 생김). REQUIRES_NEW로 호출부의 트랜잭션 유무와 무관하게 항상 독립된 새 트랜잭션에서
// 즉시 커밋시켜 두 문제를 한 번에 해결한다.
@Component
@RequiredArgsConstructor
public class WeatherDiffNotificationListener {

  private static final String TITLE = "날씨가 급변할 예정이에요";

  private final ProfileRepository profileRepository;
  private final WeatherDiffMessageBuilder messageBuilder;
  private final ApplicationEventPublisher eventPublisher;

  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(WeatherAnnouncementDiffEvent event) {
    String content = messageBuilder.buildAnnouncementMessage(event);
    notifyAffectedUsers(event.current().getGrid(), content);
  }

  @EventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
