package com.forgebackend.repository;

import com.forgebackend.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByGame_IdAndPlatformAndPlatformUserId(UUID gameId, String platform, String platformUserId);
}
