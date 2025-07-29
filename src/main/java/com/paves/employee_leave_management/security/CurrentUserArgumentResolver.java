//package com.paves.employee_leave_management.security;
//
//import com.paves.employee_leave_management.dto.UserDTO;
//import org.springframework.core.MethodParameter;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.stereotype.Component;
//import org.springframework.web.context.request.NativeWebRequest;
//import org.springframework.web.method.support.HandlerMethodArgumentResolver;
//import org.springframework.web.method.support.ModelAndViewContainer;
//
//@Component
//public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
//
//    @Override
//    public boolean supportsParameter(MethodParameter parameter) {
//        return parameter.getParameterAnnotation(CurrentUser.class) != null &&
//                parameter.getParameterType().equals(UserDTO.class);
//    }
//
//    @Override
//    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
//                                  NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
//
//        Authentication auth = (Authentication) webRequest.getUserPrincipal();
//        if (auth == null || !(auth.getPrincipal() instanceof Jwt)) {
//            return null;
//        }
//
//        Jwt jwt = (Jwt) auth.getPrincipal();
//        return new UserDTO(
//                Long.valueOf(jwt.getClaimAsString("user_id")), // Assuming user_id is an Integer
//                jwt.getClaim("email"),
//                jwt.getClaimAsStringList("roles")
//        );
//    }
//}
