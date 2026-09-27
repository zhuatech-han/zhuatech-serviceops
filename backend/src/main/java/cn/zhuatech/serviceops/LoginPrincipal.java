// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import java.util.List;
import org.springframework.security.core.userdetails.User;

/** 会话保留凭证指纹，密码修改后拒绝旧会话；不对外序列化。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
final class LoginPrincipal extends User {
  private final String fingerprint;

  LoginPrincipal(Models.Account account) {
    super(account.username, account.passwordHash, account.enabled, true, true, true, List.of());
    fingerprint = fingerprint(account.passwordHash);
  }

  /** 比较密码散列指纹，密码重设后使旧会话失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  boolean matches(Models.Account account) {
    return fingerprint.equals(fingerprint(account.passwordHash));
  }

  private static String fingerprint(String hash) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(hash.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
