package com.otboo.domain.directmessage.repository;

import com.otboo.domain.directmessage.entity.DirectMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository
    extends JpaRepository<DirectMessage, UUID>, DirectMessageRepositoryCustom {
}