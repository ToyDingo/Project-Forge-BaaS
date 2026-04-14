package com.forgebackend.steam;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevOnlySteamClientStubTest {

    private final DevOnlySteamClientStub stub = new DevOnlySteamClientStub();

    @Test
    void validHexTicket_returnsSuccessWithFixedSteamId() {
        SteamTicketValidationResult result = stub.validateTicket(480L, "any-key", "abcdef0123456789");
        assertThat(result).isInstanceOf(SteamTicketValidationResult.ValidSteamIdentity.class);
        assertThat(((SteamTicketValidationResult.ValidSteamIdentity) result).steamId64()).isEqualTo(DevOnlySteamClientStub.STUB_STEAM_ID_64);
    }

    @Test
    void shortTicket_returnsInvalid() {
        SteamTicketValidationResult result = stub.validateTicket(480L, "any-key", "abc");
        assertThat(result).isInstanceOf(SteamTicketValidationResult.InvalidSteamTicket.class);
    }

    @Test
    void nonHexTicket_returnsInvalid() {
        SteamTicketValidationResult result = stub.validateTicket(480L, "any-key", "ghijklmnopqrstuv");
        assertThat(result).isInstanceOf(SteamTicketValidationResult.InvalidSteamTicket.class);
    }
}
