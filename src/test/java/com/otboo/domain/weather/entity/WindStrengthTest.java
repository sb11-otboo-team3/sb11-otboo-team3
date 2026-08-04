package com.otboo.domain.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WindStrengthTest {

  @Test
  @DisplayName("풍속이 null이면 null을 반환한다")
  void returnsNullWhenWindSpeedIsNull() {
    assertThat(WindStrength.fromSpeed(null)).isNull();
  }

  @Test
  @DisplayName("풍속이 4.0m/s 미만이면 WEAK다")
  void returnsWeakWhenBelowFour() {
    assertThat(WindStrength.fromSpeed(3.9)).isEqualTo(WindStrength.WEAK);
  }

  @Test
  @DisplayName("풍속이 정확히 4.0m/s면 MODERATE다")
  void returnsModerateAtExactlyFour() {
    assertThat(WindStrength.fromSpeed(4.0)).isEqualTo(WindStrength.MODERATE);
  }

  @Test
  @DisplayName("풍속이 9.0m/s 미만이면 MODERATE다")
  void returnsModerateBelowNine() {
    assertThat(WindStrength.fromSpeed(8.9)).isEqualTo(WindStrength.MODERATE);
  }

  @Test
  @DisplayName("풍속이 정확히 9.0m/s면 STRONG이다")
  void returnsStrongAtExactlyNine() {
    assertThat(WindStrength.fromSpeed(9.0)).isEqualTo(WindStrength.STRONG);
  }

  @Test
  @DisplayName("풍속이 9.0m/s를 초과하면 STRONG이다")
  void returnsStrongAboveNine() {
    assertThat(WindStrength.fromSpeed(15.0)).isEqualTo(WindStrength.STRONG);
  }
}
