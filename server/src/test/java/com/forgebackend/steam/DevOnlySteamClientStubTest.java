package com.forgebackend.steam;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevOnlySteamClientStubTest {

    private final DevOnlySteamClientStub stub = new DevOnlySteamClientStub();

    @Test
    void validHexTicket_returnsDeterministicSteamIdPerTicket() {
        SteamTicketValidationResult first = stub.validateTicket(480L, "any-key", "abcdef0123456789");
        SteamTicketValidationResult second = stub.validateTicket(480L, "any-key", "abcdef0123456789");
        SteamTicketValidationResult other = stub.validateTicket(480L, "any-key", "1234567890abcdef");

        assertThat(first).isInstanceOf(SteamTicketValidationResult.ValidSteamIdentity.class);
        assertThat(second).isInstanceOf(SteamTicketValidationResult.ValidSteamIdentity.class);
        assertThat(other).isInstanceOf(SteamTicketValidationResult.ValidSteamIdentity.class);

        String firstId = ((SteamTicketValidationResult.ValidSteamIdentity) first).steamId64();
        String secondId = ((SteamTicketValidationResult.ValidSteamIdentity) second).steamId64();
        String otherId = ((SteamTicketValidationResult.ValidSteamIdentity) other).steamId64();

        assertThat(firstId).isEqualTo(secondId);
        assertThat(firstId).isNotEqualTo(otherId);
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
