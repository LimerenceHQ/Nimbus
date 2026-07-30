package cc.nimbus.event.component;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventTarget {
    Priority priority() default Priority.Normal;
}
