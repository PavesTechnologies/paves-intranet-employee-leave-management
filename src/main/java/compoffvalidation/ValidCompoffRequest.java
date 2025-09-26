package compoffvalidation;

import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import jakarta.validation.Payload;

import java.lang.annotation.*;


@Documented
@Constraint(validatedBy = CompoffRequestValidator.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCompoffRequest {
    String message() default "Invalid compoff request";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

}
