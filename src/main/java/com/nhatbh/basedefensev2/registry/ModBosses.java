package com.nhatbh.basedefensev2.registry;

import com.nhatbh.basedefensev2.boss.core.BossDefinition;
import com.nhatbh.basedefensev2.boss.impl.testboss.TestBoss;

import java.util.HashMap;
import java.util.Map;

public class ModBosses {
    private static final Map<String, BossDefinition> REGISTRY = new HashMap<>();

    public static final BossDefinition TEST_BOSS = register(TestBoss.INSTANCE);

    public static BossDefinition register(BossDefinition definition) {
        REGISTRY.put(definition.getId(), definition);
        return definition;
    }

    public static BossDefinition get(String id) {
        return REGISTRY.get(id);
    }
}
