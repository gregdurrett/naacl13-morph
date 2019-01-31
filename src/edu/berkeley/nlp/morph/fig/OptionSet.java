package edu.berkeley.nlp.morph.fig;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OptionSet {
  String name();
}

