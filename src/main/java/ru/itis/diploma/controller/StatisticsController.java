package ru.itis.diploma.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.itis.diploma.security.details.AccountUserDetails;
import ru.itis.diploma.repository.ManufacturerRepository;
import ru.itis.diploma.dto.AdvertisingIntensity;
import ru.itis.diploma.service.StatisticsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final ManufacturerRepository manufacturers;

    @GetMapping("/game/revenue-data/{manufacturerId}")
    @PreAuthorize("hasAuthority('USER')")
    public Map<Integer, BigDecimal> getRevenueData(@PathVariable Long manufacturerId,
                                                    @AuthenticationPrincipal AccountUserDetails user) {
        var target = manufacturers.findById(manufacturerId)
            .orElseThrow(() -> new AccessDeniedException("Чужая игра"));
        if (manufacturers.findByAccount_IdAndGame_Id(user.getAccount().getId(), target.getGame().getId()) == null)
            throw new AccessDeniedException("Чужая игра");
        return statisticsService.getRevenueData(manufacturerId);
    }

    @GetMapping("/game/{gameId}/advertising-intensity/{accountId}")
    @PreAuthorize("hasAuthority('USER')")
    public List<AdvertisingIntensity> getAdvertisingIntensityData(@PathVariable Long gameId,
                                                                   @PathVariable Long accountId,
                                                                   @AuthenticationPrincipal AccountUserDetails user) {
        if (!accountId.equals(user.getAccount().getId()) ||
            manufacturers.findByAccount_IdAndGame_Id(accountId, gameId) == null)
            throw new AccessDeniedException("Чужая игра");
        return statisticsService.getAdvertisingIntensityData(gameId, accountId);
    }

}
