package com.novel.splitter.validation.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean passed = true;
    private List<String> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public void addError(String error) {
        this.errors.add(error);
        this.passed = false;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void merge(ValidationResult other) {
        if (!other.isPassed()) {
            this.passed = false;
        }
        this.errors.addAll(other.getErrors());
        this.warnings.addAll(other.getWarnings());
    }
}
