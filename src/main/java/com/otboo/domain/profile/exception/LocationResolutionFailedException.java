package com.otboo.domain.profile.exception;

import com.otboo.global.error.OtbooException;
import org.springframework.http.HttpStatus;

// locationResolver.resolve(...)가 값도 에러도 없이 빈 신호로 완료되는, 정상적으로는 있을 수 없는 상태.
// 이걸 무시하고 넘어가면 사용자가 요청한 위치 갱신이 조용히 생략돼버리므로 명시적으로 실패시킨다.
public class LocationResolutionFailedException extends OtbooException {

  public LocationResolutionFailedException(double latitude, double longitude) {
    super(HttpStatus.INTERNAL_SERVER_ERROR,
        "위치 조회 결과가 비어있습니다: latitude=" + latitude + ", longitude=" + longitude);
  }
}