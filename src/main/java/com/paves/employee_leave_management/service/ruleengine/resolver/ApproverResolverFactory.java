package com.paves.employee_leave_management.service.ruleengine.resolver;

import com.paves.employee_leave_management.enums.ApproverType;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ApproverResolverFactory {

    private final List<ApproverResolver> resolvers;
    private final Map<ApproverType, ApproverResolver> resolverMap = new EnumMap<>(ApproverType.class);

    public ApproverResolverFactory(List<ApproverResolver> resolvers) {
        this.resolvers = resolvers;
    }

    @PostConstruct
    public void init() {
        for (ApproverResolver resolver : resolvers) {
            resolverMap.put(resolver.getApproverType(), resolver);
        }
    }

    public ApproverResolver getResolver(ApproverType approverType) {
        return Optional.ofNullable(resolverMap.get(approverType))
                .orElseThrow(() -> new IllegalArgumentException("No ApproverResolver found for type: " + approverType));
    }
}