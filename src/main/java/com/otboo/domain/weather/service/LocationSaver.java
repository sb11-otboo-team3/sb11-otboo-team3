package com.otboo.domain.weather.service;

import com.otboo.domain.weather.entity.Location;
import com.otboo.domain.weather.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LocationSaver {

  private final LocationRepository locationRepository;

  // 별도 트랜잭션으로 분리, saveAndFlush로 즉시 반영(동시성 문제 때문에)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveInNewTransaction(Location location) {
    locationRepository.saveAndFlush(location);
  }
}