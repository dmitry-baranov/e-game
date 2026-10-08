package ru.itis.diploma.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategyPlan;
import ru.itis.diploma.dto.StrategySnapshot;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.model.PersonalScenario;
import ru.itis.diploma.model.RecommendationHistory;
import ru.itis.diploma.repository.ManufacturerRepository;
import ru.itis.diploma.repository.PersonalScenarioRepository;
import ru.itis.diploma.repository.RecommendationHistoryRepository;
import ru.itis.diploma.repository.StrategyTrainingEpisodeRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StrategyService {
    private static final Set<String> COURSES = Set.of("CAUTIOUS", "AFFORDABLE", "QUALITY",
        "HOLD", "PRESERVE", "EXPAND", "PRICE", "PROMOTE");
    private final StrategySnapshotService snapshots;
    private final StrategyCoordinator coordinator;
    private final ManufacturerRepository manufacturers;
    private final PersonalScenarioRepository personal;
    private final RecommendationHistoryRepository history;
    private final StrategyTrainingEpisodeRepository episodes;
    private final StrategyExplanationService explanations;
    private final ObjectMapper mapper;

    @Transactional
    public StrategyAdvice recommend(Long gameId, Long accountId, String requestKey, StrategyPlan plan) {
        Manufacturer owner = snapshots.owner(gameId, accountId);
        if (requestKey == null || !requestKey.matches("[a-f0-9\\-]{36}"))
            throw new IllegalArgumentException("Некорректный запрос");
        var previous = history.findByManufacturerIdAndRequestKey(owner.getId(), requestKey);
        if (previous.isPresent()) {
            try { return mapper.readValue(previous.get().getSnapshotJson(), StrategyAdvice.class); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("Недоступна сохранённая рекомендация", ex); }
        }
        StrategySnapshot snapshot = snapshots.snapshot(gameId, accountId);
        if (plan != null) validate(plan, snapshot);
        StrategyAdvice advice = coordinator.recommend(snapshot, plan);
        if (snapshot.opened() && owner.getSelectedCourseProductCount() != null &&
            (!owner.getSelectedCourseProductCount().equals(snapshot.productCount()) ||
                owner.getSelectedCoursePrice().compareTo(snapshot.price()) != 0 ||
                owner.getSelectedCourseQuality().compareTo(snapshot.quality()) != 0 ||
                !owner.getSelectedCourseAssortment().equals(snapshot.assortment()) ||
                !owner.getSelectedCourseAdvertisingIntensity().equals(snapshot.advertisingIntensity()))) {
            advice.setCourseMismatch("Текущие параметры производства отличаются от ориентиров выбранного курса. " +
                "Игрок может оставить их без изменения или самостоятельно скорректировать форму.");
        }
        advice.setAiExplanation(explanations.explain(advice));
        if (Boolean.TRUE.equals(owner.getSaveRecommendationHistory())) {
            var record = new RecommendationHistory();
            record.setManufacturer(owner); record.setRequestKey(requestKey);
            record.setGameDay(advice.getGameDay()); record.setSelectedStrategy(owner.getSelectedStrategy());
            record.setCreatedAt(advice.getGeneratedAt());
            try { record.setSnapshotJson(mapper.writeValueAsString(advice)); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("Не удалось сохранить совет", ex); }
            record = history.save(record);
            advice.setHistoryId(record.getId());
            try { record.setSnapshotJson(mapper.writeValueAsString(advice)); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("Не удалось сохранить совет", ex); }
            history.save(record);
        }
        return advice;
    }

    @Transactional
    public void select(Long gameId, Long accountId, String course, Long historyId) {
        Manufacturer owner = snapshots.owner(gameId, accountId);
        if ((owner.isEnteredInitialProductionParameters() && Set.of("CAUTIOUS", "AFFORDABLE", "QUALITY").contains(course)) ||
            (!owner.isEnteredInitialProductionParameters() && Set.of("HOLD", "PRESERVE", "EXPAND", "PRICE", "PROMOTE").contains(course)))
            throw new IllegalArgumentException("Стратегия не подходит для текущего этапа игры");
        if (!COURSES.contains(course)) {
            if (!course.matches("PERSONAL:[0-9]+")) throw new IllegalArgumentException("Неизвестная стратегия");
            long planId = Long.parseLong(course.substring(9));
            personal.findByIdAndManufacturerId(planId, owner.getId())
                .orElseThrow(() -> new AccessDeniedException("Чужой сценарий"));
        }
        StrategyAdvice.Card chosen;
        if (historyId != null) {
            RecommendationHistory record = history.findByIdAndManufacturerId(historyId, owner.getId())
                .orElseThrow(() -> new AccessDeniedException("Чужая история"));
            chosen = historicalAdvice(gameId, accountId, historyId).getRecommendations().stream()
                .filter(card -> course.equals(card.getKey())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Такой стратегии нет в рекомендации"));
            record.setChosenStrategy(course);
            history.save(record);
        } else {
            StrategyPlan plan = course.startsWith("PERSONAL:") ?
                plan(gameId, accountId, Long.parseLong(course.substring(9))) : null;
            chosen = coordinator.recommend(snapshots.snapshot(gameId, accountId), plan).getRecommendations()
                .stream().filter(card -> course.equals(card.getKey())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Обновите предложения перед выбором курса"));
        }
        owner.setSelectedStrategy(course);
        owner.setSelectedCourseProductCount(chosen.getProductCount());
        owner.setSelectedCoursePrice(chosen.getPrice());
        owner.setSelectedCourseQuality(chosen.getQuality());
        owner.setSelectedCourseAssortment(chosen.getAssortment());
        owner.setSelectedCourseAdvertisingIntensity(chosen.getAdvertisingIntensity());
        manufacturers.save(owner);
    }

    @Transactional
    public void historyPreference(Long gameId, Long accountId, boolean enabled) {
        Manufacturer owner = snapshots.owner(gameId, accountId);
        owner.setSaveRecommendationHistory(enabled);
        manufacturers.save(owner);
    }

    @Transactional
    public void trainingPreference(Long gameId, Long accountId, boolean enabled) {
        Manufacturer owner = snapshots.owner(gameId, accountId);
        owner.setAllowStrategyTraining(enabled);
        manufacturers.save(owner);
        if (!enabled) episodes.deleteByManufacturerId(owner.getId());
    }

    @Transactional(readOnly = true)
    public List<RecommendationHistory> history(Long gameId, Long accountId) {
        return history.findByManufacturerIdOrderByCreatedAtDesc(snapshots.owner(gameId, accountId).getId());
    }

    @Transactional(readOnly = true)
    public StrategyAdvice historicalAdvice(Long gameId, Long accountId, Long historyId) {
        RecommendationHistory record = history.findByIdAndManufacturerId(historyId,
            snapshots.owner(gameId, accountId).getId())
            .orElseThrow(() -> new AccessDeniedException("Чужая история"));
        try { return mapper.readValue(record.getSnapshotJson(), StrategyAdvice.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Недоступна сохранённая рекомендация", ex); }
    }

    @Transactional(readOnly = true)
    public List<PersonalScenario> plans(Long gameId, Long accountId) {
        return personal.findByManufacturerIdOrderByUpdatedAtDesc(snapshots.owner(gameId, accountId).getId());
    }

    @Transactional(readOnly = true)
    public StrategyPlan plan(Long gameId, Long accountId, Long id) {
        return convert(personal.findByIdAndManufacturerId(id, snapshots.owner(gameId, accountId).getId())
            .orElseThrow(() -> new AccessDeniedException("Чужой сценарий")));
    }

    @Transactional
    public Long save(Long gameId, Long accountId, StrategyPlan plan) {
        Manufacturer owner = snapshots.owner(gameId, accountId);
        validate(plan, snapshots.snapshot(gameId, accountId));
        PersonalScenario entity = plan.getId() == null ? new PersonalScenario() :
            personal.findByIdAndManufacturerId(plan.getId(), owner.getId())
                .orElseThrow(() -> new AccessDeniedException("Чужой сценарий"));
        entity.setManufacturer(owner);
        entity.setName(plan.getName().trim());
        entity.setProductCount(plan.getProductCount()); entity.setCapacity(plan.getCapacity());
        entity.setPrice(plan.getPrice()); entity.setQuality(plan.getQuality());
        entity.setAssortment(plan.getAssortment()); entity.setAdvertisingIntensity(plan.getAdvertisingIntensity());
        entity.setAdvertisingDays(plan.getAdvertisingDays()); entity.setUpdatedAt(LocalDateTime.now());
        entity.setBusinessCreditAmount(plan.getBusinessCreditAmount() == null ? BigDecimal.ZERO : plan.getBusinessCreditAmount());
        return personal.save(entity).getId();
    }

    @Transactional
    public void delete(Long gameId, Long accountId, Long planId) {
        Manufacturer owner = snapshots.owner(gameId, accountId);
        PersonalScenario entity = personal.findByIdAndManufacturerId(planId, owner.getId())
            .orElseThrow(() -> new AccessDeniedException("Чужой сценарий"));
        if (("PERSONAL:" + planId).equals(owner.getSelectedStrategy())) {
            owner.setSelectedStrategy(null);
            owner.setSelectedCourseProductCount(null);
            owner.setSelectedCoursePrice(null);
            owner.setSelectedCourseQuality(null);
            owner.setSelectedCourseAssortment(null);
            owner.setSelectedCourseAdvertisingIntensity(null);
            manufacturers.save(owner);
        }
        personal.delete(entity);
    }

    private StrategyPlan convert(PersonalScenario entity) {
        var plan = new StrategyPlan();
        plan.setId(entity.getId()); plan.setName(entity.getName());
        plan.setProductCount(entity.getProductCount()); plan.setCapacity(entity.getCapacity());
        plan.setPrice(entity.getPrice()); plan.setQuality(entity.getQuality());
        plan.setAssortment(entity.getAssortment()); plan.setAdvertisingIntensity(entity.getAdvertisingIntensity());
        plan.setAdvertisingDays(entity.getAdvertisingDays());
        plan.setBusinessCreditAmount(entity.getBusinessCreditAmount());
        return plan;
    }

    private void validate(StrategyPlan p, StrategySnapshot s) {
        if (p.getName() == null || p.getName().isBlank() || p.getName().length() > 100 ||
            p.getProductCount() == null || p.getProductCount() < 1 || p.getProductCount() > 1000000 ||
            p.getCapacity() == null || p.getCapacity() < 1 || p.getCapacity() > 1000000 ||
            (s.opened() && !p.getCapacity().equals(s.capacity())) ||
            p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0 ||
            p.getQuality() == null || p.getQuality().compareTo(BigDecimal.ZERO) < 0 ||
            p.getQuality().compareTo(BigDecimal.ONE) > 0 ||
            p.getAssortment() == null || p.getAssortment() < 1 || p.getAssortment() > p.getProductCount() ||
            p.getAdvertisingIntensity() == null || p.getAdvertisingIntensity() < 0 || p.getAdvertisingIntensity() > 7 ||
            p.getAdvertisingDays() == null || p.getAdvertisingDays() < 0 ||
            (p.getBusinessCreditAmount() != null && p.getBusinessCreditAmount().signum() < 0) ||
            (!s.opened() && p.getBusinessCreditAmount() != null && p.getBusinessCreditAmount().signum() > 0) ||
            (p.getAdvertisingDays() == 0) != (p.getAdvertisingIntensity() == 0) ||
            p.getAdvertisingDays() > (p.getProductCount() + p.getCapacity() - 1) / p.getCapacity()) {
            throw new IllegalArgumentException("Недопустимые параметры сценария");
        }
    }
}
