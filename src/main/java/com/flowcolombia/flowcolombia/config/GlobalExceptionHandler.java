package com.flowcolombia.flowcolombia.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ModelAndView handleAllExceptions(Exception ex) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("message", "Ha ocurrido un error inesperado. Por favor, inténtalo de nuevo más tarde.");
        mav.addObject("detail", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(org.springframework.web.servlet.NoHandlerFoundException.class)
    public ModelAndView handleNotFound() {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("message", "La página que buscas no existe.");
        mav.addObject("detail", "Error 404 - Página no encontrada");
        return mav;
    }
}