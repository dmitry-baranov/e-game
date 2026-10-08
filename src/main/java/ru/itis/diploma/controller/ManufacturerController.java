package ru.itis.diploma.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import ru.itis.diploma.dto.CommonProductionParameters;
import ru.itis.diploma.dto.InitialProductionParameters;
import ru.itis.diploma.dto.ManufacturerFinancialStatus;
import ru.itis.diploma.dto.ManufacturerParameters;
import ru.itis.diploma.dto.NewProductionParameters;
import ru.itis.diploma.model.Game;
import ru.itis.diploma.security.details.AccountUserDetails;
import ru.itis.diploma.service.AccountService;
import ru.itis.diploma.service.GameService;
import ru.itis.diploma.service.ManufacturerService;
import ru.itis.diploma.repository.ManufacturerRepository;
import ru.itis.diploma.service.StrategySnapshotService;
import java.util.Objects;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ManufacturerController {

    private final ManufacturerService manufacturerService;
    private final AccountService accountService;
    private final GameService gameService;
    private final ManufacturerRepository manufacturerRepository;
    private final StrategySnapshotService strategySnapshotService;

    @PreAuthorize("hasAuthority('USER')")
    @PostMapping("/game/{id}/initial-production-parameters")
    public String defineInitialProductionParameters(@AuthenticationPrincipal AccountUserDetails userDetails,
                                                    @PathVariable("id") Long gameId,
                                                    InitialProductionParameters initialProductionParameters,
                                                    Model model) {
        Game game = gameService.getGameById(gameId);
        strategySnapshotService.owner(gameId, userDetails.getAccount().getId());
        if (notValidAdvertisingParameters(initialProductionParameters)) {
            model.addAttribute("game", game);
            model.addAttribute("errorMessage", "Некорректные параметры рекламы");
            return "define_initial_production_params";
        }
        try {
            manufacturerService.defineInitialProductionParameters(initialProductionParameters, userDetails.getAccount().getId(), game);
        } catch (IllegalArgumentException e) {
            model.addAttribute("game", game);
            model.addAttribute("errorMessage", e.getMessage());
            return "define_initial_production_params";
        }
        return "redirect:/game/" + gameId;
    }

    @PreAuthorize("hasAuthority('USER')")
    @PostMapping("/game/{id}/production-parameters")
    public String defineNewProductionParameters(@AuthenticationPrincipal AccountUserDetails userDetails,
                                                @PathVariable("id") Long gameId,
                                                NewProductionParameters newProductionParameters,
                                                Model model) {
        var game = gameService.getGameById(gameId);
        strategySnapshotService.owner(gameId, userDetails.getAccount().getId());
        if (notValidAdvertisingParameters(newProductionParameters)) {
            model.addAttribute("game", game);
            model.addAttribute("manufacturer", manufacturerService.getManufacturerByAccountIdAndGameId(
                userDetails.getAccount().getId(), gameId));
            model.addAttribute("errorMessage", "Некорректные параметры рекламы");
            return "define_new_production_params";
        }
        try {
            manufacturerService.defineNewProductionParameters(newProductionParameters, userDetails.getAccount().getId(), game);
        } catch (IllegalArgumentException e) {
            model.addAttribute("game", game);
            model.addAttribute("manufacturer", manufacturerService.getManufacturerByAccountIdAndGameId(
                userDetails.getAccount().getId(), gameId));
            model.addAttribute("errorMessage", e.getMessage());
            return "define_new_production_params";
        }
        return "redirect:/game/" + gameId;
    }

    @GetMapping("/game/{id}/production-parameters")
    @PreAuthorize("hasAuthority('USER')")
    public String defineNewProductionParameters(@AuthenticationPrincipal AccountUserDetails userDetails,
                                                @PathVariable("id") Long gameId,
                                                Model model) {
        var game = gameService.getGameById(gameId);
        strategySnapshotService.owner(gameId, userDetails.getAccount().getId());
        model.addAttribute("game", game);
        var manufacturer = manufacturerService.getManufacturerByAccountIdAndGameId(userDetails.getAccount().getId(), gameId);
        if (manufacturer == null || !manufacturer.isEnteredInitialProductionParameters())
            throw new AccessDeniedException("Чужая игра");
        model.addAttribute("manufacturer", manufacturer);
        return "define_new_production_params";
    }

    @ResponseBody
    @PreAuthorize("hasAuthority('ADMIN')")
    @GetMapping("/game/{id}/manufacturers-parameters")
    public List<ManufacturerParameters> getManufacturersParameters(@PathVariable("id") Long gameId) {
        return manufacturerService.getManufacturersActualProductionParameters(gameId);
    }

    @ResponseBody
    @GetMapping("/game/{id}/financial-status/{accountId}")
    @PreAuthorize("isAuthenticated()")
    public ManufacturerFinancialStatus getFinancialStatus(@PathVariable("id") Long gameId,
                                                           @PathVariable Long accountId,
                                                           @AuthenticationPrincipal AccountUserDetails user) {
        if (!user.getAccount().getRole().equals(ru.itis.diploma.model.Account.Role.ADMIN) &&
            !user.getAccount().getId().equals(accountId)) throw new AccessDeniedException("Чужие финансы");
        if (manufacturerService.getManufacturerByAccountIdAndGameId(accountId, gameId) == null)
            throw new AccessDeniedException("Чужая игра");
        var game = gameService.getGameById(gameId);
        return manufacturerService.getManufacturerFinancialStatus(game, accountId);
    }

    @ResponseBody
    @PreAuthorize("hasAuthority('USER')")
    @GetMapping("/manufacturer/{id}/competitors-data")
    public List<ManufacturerParameters> getCompetitorsData(@PathVariable Long id,
                                                            @AuthenticationPrincipal AccountUserDetails user) {
        var manufacturer = manufacturerRepository.findById(id)
            .orElseThrow(() -> new AccessDeniedException("Чужая игра"));
        if (!manufacturer.getAccount().getId().equals(user.getAccount().getId()))
            throw new AccessDeniedException("Чужая игра");
        return manufacturerService.getCompetitorsData(id);
    }

    private boolean notValidAdvertisingParameters(CommonProductionParameters commonProductionParameters) {
        if (commonProductionParameters.getAdvertisingIntensityIndex() == null ||
            commonProductionParameters.getAdvertisingDays() == null) return true;
        return Objects.equals(commonProductionParameters.getAdvertisingIntensityIndex(), 0) !=
            Objects.equals(commonProductionParameters.getAdvertisingDays(), 0);
    }
}
