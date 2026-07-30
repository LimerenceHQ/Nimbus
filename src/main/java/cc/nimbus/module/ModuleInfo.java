package cc.nimbus.module;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleInfo {
    String name();
    String displayName() default "";
    String[] alias() default {};
    String description() default "";
    ModuleCategory category();
    int keyBind() default 0;
    boolean enableOnStartUp() default false;
}
