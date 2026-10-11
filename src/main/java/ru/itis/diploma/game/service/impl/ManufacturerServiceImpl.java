package ru.itis.diploma.game.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.game.dto.*;
import ru.itis.diploma.game.model.*;
import ru.itis.diploma.game.repository.*;
import ru.itis.diploma.game.service.*;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import ru.itis.diploma.game.model.enums.GameStatus;

@Service
@RequiredArgsConstructor
public class ManufacturerServiceImpl implements ManufacturerService {

    private final ManufacturerRepository manufacturerRepository;
    private final AccountService accountService;
    private final ProductionParametersRepository productionParametersRepository;
    private final AdvertisementRepository advertisementRepository;
    private final BusinessCreditPaymentRepository businessCreditPaymentRepository;
    private final InvestmentCreditPaymentRepository investmentCreditPaymentRepository;
    private final GameRepository gameRepository;
    private final TradingSessionResultsRepository tradingSessionResultsRepository;
    private final StrategyTrainingCapture trainingCapture;
    private static final int MONTH = 30;

    @Override
    public Optional<ProductionParameters> getLastProductionParameters(Long manufacturerId) {
        return productionParametersRepository.findFirstByManufacturerIdOrderByStartDateDescIdDesc(manufacturerId);
    }

    @Override
    public Optional<Advertisement> getLastAdvertisement(Long manufacturerId) {
        return advertisementRepository.findFirstByManufacturerIdOrderByStartDateDescIdDesc(manufacturerId);
    }

    @Override
    public Optional<ProductionParameters> getActualProductionParameters(Long manufacturerId) {
        var actualParameters = productionParametersRepository.findByManufacturerId(manufacturerId).stream()
            .filter(p -> p.getStartDate() >= p.getManufacturer().getGame().getCurrentDay() - p.getTimeToMarket())
            .toList();
        if (actualParameters.isEmpty()) {
            return Optional.empty();
        } else if (actualParameters.size() == 1) {
            return Optional.of(actualParameters.get(0));
        } else {
            throw new IllegalStateException(
                "Некорректное состояние системы - несколько актуальных параметров производства игрока");
        }
    }

    @Override
    public Optional<Advertisement> getActualAdvertisement(Long manufacturerId) {
        var actualAdvertisements = advertisementRepository.findByManufacturerId(manufacturerId).stream()
            .filter(a -> a.getEndDate() >= a.getManufacturer().getGame().getCurrentDay())
            .toList();
        if (actualAdvertisements.isEmpty()) {
            return Optional.empty();
        } else if (actualAdvertisements.size() == 1) {
            return Optional.of(actualAdvertisements.get(0));
        } else {
            throw new IllegalStateException(
                "Некорректное состояние системы - несколько актуальных параметров рекламы игрока");
        }
    }

    @Override
    public List<Manufacturer> getGameManufacturers(Long gameId) {
        return manufacturerRepository.findByGame_IdAndEnteredInitialProductionParametersIsTrue(gameId);
    }

    @Override
    public List<Manufacturer> getCreateGameManufacturers(Long gameId) {
        return manufacturerRepository.findByGame_Id(gameId);
    }

    @Override
    public List<ManufacturerParameters> getManufacturersActualProductionParameters(Long gameId) {
        return getCreateGameManufacturers(gameId).stream()
            .map(manufacturer -> {
                var account = accountService.getById(manufacturer.getAccount().getId());
                var productionParameters = getLastProductionParameters(manufacturer.getId());
                var advertisement = getLastAdvertisement(manufacturer.getId());
                ManufacturerParameters manufacturerParameters = ManufacturerParameters.builder()
                    .id(manufacturer.getId())
                    .fullName(account.getFullName())
                    .investmentCreditAmount(manufacturer.getInvestmentCreditAmount())
                    .balance(manufacturer.getBalance())
                    .build();
                productionParameters.ifPresent(p -> {
                    manufacturerParameters.setTimeToMarket(p.getTimeToMarket());
                    manufacturerParameters.setCostPrice(p.getCostPrice());
                    manufacturerParameters.setPrice(p.getPrice());
                    manufacturerParameters.setProductCount(p.getProductCount());
                    manufacturerParameters.setAssortment(p.getAssortment());
                    manufacturerParameters.setProductionCapacityPerDay(p.getProductionCapacityPerDay());
                    manufacturerParameters.setQualityIndex(p.getQualityIndex());
                    manufacturerParameters.setBusinessCreditAmount(p.getBusinessCreditAmount());
                    manufacturerParameters.setInterestRateBusinessCredit(p.getInterestRateBusinessCredit());
                    manufacturerParameters.setStartDate(p.getStartDate());
                });
                advertisement.ifPresent(a -> {
                    manufacturerParameters.setAdvertisingStartDate(a.getStartDate());
                    manufacturerParameters.setAdvertisingEndDate(a.getEndDate());
                    manufacturerParameters.setAdvertisingIntensityIndex(a.getIntensityIndex());
                    manufacturerParameters.setAdvertisingCost(a.getCost());
                });
                return manufacturerParameters;
            }).toList();
    }

    @Override
    @Transactional
    public ProductionParameters defineInitialProductionParameters(InitialProductionParameters initialProductionParameters,
                                                                  Long accountId, Game game) {
        game = gameRepository.lockById(game.getId()).orElseThrow();
        var manufacturer = getManufacturerByAccountIdAndGameId(accountId, game.getId());
        if (manufacturer == null || manufacturer.isEnteredInitialProductionParameters() || game.getStatus() == GameStatus.FINISHED)
            throw new IllegalArgumentException("Нельзя повторно открыть предприятие");
        if (initialProductionParameters.getProductionCapacityPerDay() == null ||
            initialProductionParameters.getProductionCapacityPerDay() < 1)
            throw new IllegalArgumentException("Некорректная мощность");
        validateAndCalculate(initialProductionParameters, game, initialProductionParameters.getProductionCapacityPerDay());
        var trainingEpisode = trainingCapture.capture(manufacturer, initialProductionParameters);
        initialProductionParameters.setInvestmentCreditAmount(initialProductionParameters.getCostPrice()
            .multiply(BigDecimal.valueOf(initialProductionParameters.getProductCount()))
            .add(initialProductionParameters.getAdvertisingCost())
            .add(game.getProductPower().multiply(BigDecimal.valueOf(
                (initialProductionParameters.getProductionCapacityPerDay() + 9) / 10))));
        manufacturer.setInvestmentCreditAmount(initialProductionParameters.getInvestmentCreditAmount());
        manufacturer.setEnteredInitialProductionParameters(true);
        manufacturer.setBalance(BigDecimal.ZERO);
        manufacturer.setInvestmentCreditTermMonths(game.getInvestmentCreditTermMonths());
        manufacturer.setInvestmentCreditDebt(initialProductionParameters.getInvestmentCreditAmount());
        manufacturer.setProductionCapacityPerDay(initialProductionParameters.getProductionCapacityPerDay());
        manufacturerRepository.save(manufacturer);

        saveAdvertisement(manufacturer, initialProductionParameters);
        var productionParameters = saveProductParameters(manufacturer, initialProductionParameters);
        trainingCapture.linkCycle(trainingEpisode, productionParameters);
        productionParameters.setBusinessCreditAmount(BigDecimal.ZERO);
        var payment = InvestmentCreditPayment.builder()
            .date(game.getCurrentDay())
            .manufacturer(manufacturer)
            .principalPayment(BigDecimal.ZERO)
            .interestAmount(BigDecimal.ZERO)
            .nextDate(game.getCurrentDay() + MONTH)
            .build();
        investmentCreditPaymentRepository.save(payment);
        return productionParameters;
    }

    @Override
    @Transactional
    public ProductionParameters defineNewProductionParameters(NewProductionParameters newProductionParameters,
                                                              Long accountId, Game game) {
        game = gameRepository.lockById(game.getId()).orElseThrow();
        var manufacturer = getManufacturerByAccountIdAndGameId(accountId, game.getId());
        if (manufacturer == null || !manufacturer.isEnteredInitialProductionParameters() || game.getStatus() == GameStatus.FINISHED)
            throw new IllegalArgumentException("Предприятие недоступно");
        if (getActualProductionParameters(manufacturer.getId()).isPresent())
            throw new IllegalArgumentException("Текущий цикл ещё не завершён");
        validateAndCalculate(newProductionParameters, game, manufacturer.getProductionCapacityPerDay());
        newProductionParameters.setProductionCosts(newProductionParameters.getCostPrice()
            .multiply(BigDecimal.valueOf(newProductionParameters.getProductCount()))
            .add(newProductionParameters.getAdvertisingCost()));
        if (newProductionParameters.getBusinessCreditAmount() == null ||
            newProductionParameters.getBusinessCreditAmount().signum() < 0 ||
            manufacturer.getBalance().add(newProductionParameters.getBusinessCreditAmount())
                .compareTo(newProductionParameters.getProductionCosts()) < 0)
            throw new IllegalArgumentException("Недостаточно средств для производства");
        var trainingEpisode = trainingCapture.capture(manufacturer, newProductionParameters);
        manufacturer.setBalance(manufacturer.getBalance()
            .add(newProductionParameters.getBusinessCreditAmount().subtract(newProductionParameters.getProductionCosts())));
        manufacturerRepository.save(manufacturer);
        saveAdvertisement(manufacturer, newProductionParameters);
        var productionParameters = saveProductParameters(manufacturer, newProductionParameters);
        trainingCapture.linkCycle(trainingEpisode, productionParameters);
        productionParameters.setInterestRateBusinessCredit(manufacturer.getGame().getInterestRateBusinessCredit());
        productionParameters.setBusinessCreditAmount(newProductionParameters.getBusinessCreditAmount());
        if (productionParameters.getBusinessCreditAmount().compareTo(BigDecimal.ZERO) > 0) {
            var payment = BusinessCreditPayment.builder()
                .productionParameters(productionParameters)
                .amount(BigDecimal.ZERO)
                .nextAmount(productionParameters.getBusinessCreditAmount()
                    .add(PaymentService.calculateBusinessCreditInterestAmount(
                        productionParameters.getBusinessCreditAmount(),
                        game.getInterestRateBusinessCredit(),
                        productionParameters.getTimeToMarket())))
                .nextDate(game.getCurrentDay() + productionParameters.getTimeToMarket() * 2)
                .build();
            businessCreditPaymentRepository.save(payment);
        }
        productionParametersRepository.save(productionParameters);
        return productionParameters;
    }

    @Override
    public ManufacturerFinancialStatus getManufacturerFinancialStatus(Game game, Long accountId) {
        var manufacturer = getManufacturerByAccountIdAndGameId(accountId, game.getId());
        var actualProductionParameters = getLastProductionParameters(manufacturer.getId()).get();
        var activeManufacturingCycle = game.getCurrentDay() <= (actualProductionParameters.getStartDate() +
            actualProductionParameters.getTimeToMarket());

        return ManufacturerFinancialStatus.builder()
            .day(game.getCurrentDay())
            .balance(manufacturer.getBalance())
            .todayRevenue(tradingSessionResultsRepository.findByManufacturerId(manufacturer.getId()).stream()
                .filter(result -> result.getTradeDate().equals(game.getCurrentDay()))
                .map(result -> result.getPrice().multiply(BigDecimal.valueOf(result.getProductNumber())))
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .investmentCreditDebt(calculateManufacturerInvestmentCreditDebt(manufacturer, game))
            .businessCreditDebt(calculateManufacturerBusinessCreditDebt(manufacturer))
            .timeToMarketEndDay(actualProductionParameters.getStartDate() + actualProductionParameters.getTimeToMarket())
            .activeManufacturingCycle(activeManufacturingCycle)
            .build();
    }

    @Override
    public BigDecimal calculateManufacturerInvestmentCreditDebt(Manufacturer manufacturer, Game game) {
        var lastPayment = investmentCreditPaymentRepository.findAllByManufacturerId(
                manufacturer.getId()).stream()
            .max(Comparator.comparingInt(InvestmentCreditPayment::getDate)).get();
        return manufacturer.getInvestmentCreditDebt()
            .add(calculateInvestmentCreditInterestAmountOnCurrentDay(
                manufacturer.getInvestmentCreditDebt(),
                game.getInvestmentCreditTermMonths(),
                game.getCurrentDay() - lastPayment.getDate()));
    }

    private BigDecimal calculateInvestmentCreditInterestAmountOnCurrentDay(BigDecimal investmentCreditDebt,
                                                                           BigDecimal interestRateInvestmentCredit,
                                                                           int daysAfterLastPayment) {
        return (interestRateInvestmentCredit.divide(BigDecimal.valueOf(100), 2, RoundingMode.UP))
            .multiply(investmentCreditDebt)
            .multiply(BigDecimal.valueOf(daysAfterLastPayment))
            .divide(BigDecimal.valueOf(365), 2, RoundingMode.UP);
    }

    @Override
    public BigDecimal calculateManufacturerBusinessCreditDebt(Manufacturer manufacturer) {
        List<ProductionParameters> manufacturerProductionParameters = productionParametersRepository.findByManufacturerId(manufacturer.getId());
        return businessCreditPaymentRepository.findAllByProductionParametersIdInAndNextDateAfter(
                manufacturerProductionParameters.stream()
                    .map(ProductionParameters::getId)
                    .toList(),
                manufacturer.getGame().getCurrentDay()).stream()
            .map(BusinessCreditPayment::getNextAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public List<ManufacturerParameters> getAllProductionParameters(Manufacturer manufacturer) {
        return productionParametersRepository.findByManufacturerId(manufacturer.getId()).stream()
            .map(productionParameters -> {
                var advertisementOptional = advertisementRepository.findByManufacturerIdAndStartDate(
                    manufacturer.getId(), productionParameters.getStartDate() + 1);
                var manufacturerParameters = ManufacturerParameters.builder()
                    .startDate(productionParameters.getStartDate())
                    .timeToMarket(productionParameters.getTimeToMarket())
                    .costPrice(productionParameters.getCostPrice())
                    .price(productionParameters.getPrice())
                    .productCount(productionParameters.getProductCount())
                    .assortment(productionParameters.getAssortment())
                    .productionCapacityPerDay(productionParameters.getProductionCapacityPerDay())
                    .qualityIndex(productionParameters.getQualityIndex())
                    .businessCreditAmount(productionParameters.getBusinessCreditAmount())
                    .interestRateBusinessCredit(productionParameters.getInterestRateBusinessCredit())
                    .build();
                if (advertisementOptional.isPresent()) {
                    var advertisement = advertisementOptional.get();
                    manufacturerParameters.setAdvertisingStartDate(advertisement.getStartDate());
                    manufacturerParameters.setAdvertisingEndDate(advertisement.getEndDate());
                    manufacturerParameters.setAdvertisingIntensityIndex(advertisement.getIntensityIndex());
                    manufacturerParameters.setAdvertisingCost(advertisement.getCost());
                }
                return manufacturerParameters;
            }).sorted(Comparator.comparing(ManufacturerParameters::getStartDate).reversed())
            .toList();
    }

    @Override
    public List<ManufacturerParameters> getCompetitorsData(Long id) {
        var manufacturer = manufacturerRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        var gameId = manufacturer.getGame().getId();
        return getGameManufacturers(gameId).stream()
            .filter(m -> !Objects.equals(m.getId(), manufacturer.getId()))
            .map(m -> getLastProductionParameters(m.getId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(p -> {
                Optional<Advertisement> lastAdvertisement = advertisementRepository.findByManufacturerIdAndStartDate(
                    p.getManufacturer().getId(), p.getStartDate() + 1);
                var advertisingIntensityIndex = lastAdvertisement.isPresent() ? lastAdvertisement.get().getIntensityIndex() : 0;
                return ManufacturerParameters.builder()
                    .id(p.getManufacturer().getId())
                    .fullName(p.getManufacturer().getAccount().getFullName())
                    .assortment(p.getAssortment())
                    .price(p.getPrice())
                    .qualityIndex(p.getQualityIndex())
                    .advertisingIntensityIndex(advertisingIntensityIndex)
                    .build();
            })
            .toList();
    }

    @Override
    public Manufacturer getManufacturerByAccountIdAndGameId(Long accountId, Long gameId) {
        return manufacturerRepository.findByAccount_IdAndGame_Id(accountId, gameId);
    }

    private void saveAdvertisement(Manufacturer manufacturer, CommonProductionParameters productionParameters) {
        if (productionParameters.getAdvertisingIntensityIndex() != 0 && productionParameters.getAdvertisingDays() != 0) {
            var advertisement = Advertisement.builder()
                .manufacturer(manufacturer)
                .cost(productionParameters.getAdvertisingCost())
                .intensityIndex(productionParameters.getAdvertisingIntensityIndex())
                .startDate(manufacturer.getGame().getCurrentDay() + 1)
                .endDate(manufacturer.getGame().getCurrentDay() + productionParameters.getAdvertisingDays())
                .build();
            advertisementRepository.save(advertisement);
        }
    }

    private void validateAndCalculate(CommonProductionParameters p, Game game, int capacity) {
        if (p.getProductCount() == null || p.getProductCount() < 1 || p.getProductCount() > 1000000 ||
            p.getAssortment() == null || p.getAssortment() < 1 || p.getAssortment() > p.getProductCount() ||
            p.getQualityIndex() == null || p.getQualityIndex().signum() < 0 ||
            p.getQualityIndex().compareTo(BigDecimal.ONE) > 0 ||
            p.getPrice() == null || p.getPrice().signum() <= 0 ||
            p.getAdvertisingIntensityIndex() == null || p.getAdvertisingIntensityIndex() < 0 ||
            p.getAdvertisingIntensityIndex() > 7 || p.getAdvertisingDays() == null || p.getAdvertisingDays() < 0 ||
            (p.getAdvertisingDays() == 0) != (p.getAdvertisingIntensityIndex() == 0))
            throw new IllegalArgumentException("Некорректные параметры производства");
        int days = (p.getProductCount() + capacity - 1) / capacity;
        if (p.getAdvertisingDays() > days) throw new IllegalArgumentException("Реклама дольше производственного цикла");
        p.setTimeToMarket(days);
        double n = (double) p.getProductCount() / p.getAssortment();
        BigDecimal coefficient = n > 1000 ? new BigDecimal("0.7") : n > 100 ? new BigDecimal("0.9") : BigDecimal.ONE;
        p.setCostPrice(game.getBaseCostPrice().multiply(p.getQualityIndex()).multiply(coefficient)
            .setScale(2, RoundingMode.HALF_UP));
        p.setAdvertisingCost(game.getBaseAdvertisementPrice()
            .multiply(BigDecimal.valueOf(p.getAdvertisingIntensityIndex()))
            .multiply(BigDecimal.valueOf(p.getAdvertisingDays())).setScale(2, RoundingMode.HALF_UP));
    }

    private ProductionParameters saveProductParameters(Manufacturer manufacturer, CommonProductionParameters productionParameters) {
        var resultProductionParameters = ProductionParameters.builder()
            .manufacturer(manufacturer)
            .productCount(productionParameters.getProductCount())
            .price(productionParameters.getPrice())
            .costPrice(productionParameters.getCostPrice())
            .assortment(productionParameters.getAssortment())
            .qualityIndex(productionParameters.getQualityIndex())
            .productionCapacityPerDay(manufacturer.getProductionCapacityPerDay())
            .startDate(manufacturer.getGame().getCurrentDay())
            .endDate(manufacturer.getGame().getCurrentDay() + productionParameters.getTimeToMarket())
            .timeToMarket(productionParameters.getTimeToMarket())
            .build();
        productionParametersRepository.save(resultProductionParameters);

        return resultProductionParameters;
    }

}
