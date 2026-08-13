package com.otboo.global.validation;

import java.util.regex.Pattern;

public final class StrongPasswordPolicy {

  private static final Pattern PATTERN = Pattern.compile(
      "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{10,}$"
  );

  public static final String DESCRIPTION =
      "비밀번호는 10자 이상이며, 영문자·숫자·특수문자(@$!%*?&)를 각각 1개 이상 포함해야 합니다.";

  private StrongPasswordPolicy() {
  }

  public static boolean isSatisfiedBy(String password) {
    return password != null && PATTERN.matcher(password).matches();
  }
}