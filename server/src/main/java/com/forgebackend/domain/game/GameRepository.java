package com.forgebackend.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByApiKeyLookupHash(String apiKeyLookupHash);
}
