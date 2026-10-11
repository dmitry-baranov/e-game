package ru.itis.diploma.experiment.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.itis.diploma.experiment.service.ExperimentManager;

import java.util.Map;

/** Сохраняет прежний URL отчёта, не заставляя управление серией зависеть от аналитики. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/bot-experiments")
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "true")
public class ExperimentReportController {
    private final ExperimentManager manager;
    private final ExperimentReports reports;

    @GetMapping("/{id}/report")
    public Map<String, Object> report(@PathVariable Long id) {
        manager.get(id);
        return reports.report(id);
    }
}
