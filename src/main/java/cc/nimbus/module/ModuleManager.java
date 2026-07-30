package cc.nimbus.module;

import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.handler.EventManager;
import cc.nimbus.event.impl.input.EventKeyPress;
import cc.nimbus.module.impl.hud.ActiveList;
import cc.nimbus.module.impl.hud.PostProcess;
import cc.nimbus.module.impl.render.ClickGui;
import cc.nimbus.module.impl.render.ESP;

import java.util.*;

public final class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    private final List<BasicModule> modules = new ArrayList<>();
    private final Map<Class<? extends BasicModule>, BasicModule> byClass = new HashMap<>();
    private final Map<ModuleCategory, List<BasicModule>> byCategory = new EnumMap<>(ModuleCategory.class);
    private final Map<String, BasicModule> byName = new HashMap<>();
    private final Map<Integer, List<BasicModule>> byKey = new HashMap<>();
    public boolean init;

    private ModuleManager() {}
    public static ModuleManager getInstance() { return INSTANCE; }

    public synchronized void init() {
        clear();

        registerModules(
                new ClickGui(),
                new PostProcess(),
                new ActiveList(),
                new ESP());

        EventManager.register(this);

        init = true;

        System.out.println("[ModuleManager] Successfully loaded " + modules.size() + " modules.");
    }

    public synchronized void clear() {
        for (BasicModule module : modules) module.setEnabled(false);
        modules.clear(); byClass.clear(); byCategory.clear(); byName.clear(); byKey.clear();
    }

    public synchronized void registerModules(BasicModule... values) { for (BasicModule module : values) registerModule(module); }
    public synchronized void registerModule(BasicModule module) {
        Objects.requireNonNull(module, "module");
        if (byClass.containsKey(module.getClass())) throw new IllegalArgumentException("Duplicate module class: " + module.getClass().getName());
        Set<String> names = new LinkedHashSet<>(); names.add(module.getName()); names.addAll(Arrays.asList(module.getAliases()));
        for (String name : names) {
            String normalized = normalize(name);
            if (normalized.isEmpty()) throw new IllegalArgumentException("Empty module name or alias");
            if (byName.containsKey(normalized)) throw new IllegalArgumentException("Duplicate module name or alias: " + name);
        }
        modules.add(module); byClass.put(module.getClass(), module); byCategory.computeIfAbsent(module.getCategory(), key -> new ArrayList<>()).add(module);
        for (String name : names) byName.put(normalize(name), module);
        modules.sort(Comparator.comparing(BasicModule::getName, String.CASE_INSENSITIVE_ORDER));
        rebuildKeyMap();
    }

    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    public synchronized void rebuildKeyMap() { byKey.clear(); for (BasicModule module : modules) if (module.getKey() != 0) byKey.computeIfAbsent(module.getKey(), key -> new ArrayList<>()).add(module); }
    public List<BasicModule> getModules() { return Collections.unmodifiableList(modules); }
    public List<BasicModule> getModulesByCategory(ModuleCategory category) { return Collections.unmodifiableList(byCategory.getOrDefault(category, List.of())); }
    public synchronized List<BasicModule> getModulesByKey(int key) {
        List<BasicModule> bound = byKey.get(key);
        return bound == null ? List.of() : List.copyOf(bound);
    }
    @SuppressWarnings("unchecked") public <T extends BasicModule> T getModule(Class<T> type) { return (T) byClass.get(type); }
    public BasicModule getModule(String name) { return byName.get(normalize(name)); }
    public void postInit() { modules.stream().filter(BasicModule::shouldAutoEnable).forEach(module -> module.setEnabled(true)); }

    @EventTarget
    public void onKey(EventKeyPress event) {
        if (event.getAction() != 1) return;
        List<BasicModule> bound = byKey.get(event.getKey());
        if (bound != null) List.copyOf(bound).forEach(BasicModule::toggle);
    }
}
