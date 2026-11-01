package com.mobilebadge.security;

public enum SessionAccessMode {
  /** 仅所属顾问（含本人创建的主管/管理员会话）。 */
  OWNER,
  /** 所属顾问，或本组织主管/管理员只读。 */
  READ
}
