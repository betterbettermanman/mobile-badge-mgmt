package com.mobilebadge.seed;

import com.mobilebadge.config.AppProperties;
import com.mobilebadge.domain.ConsentPolicy;
import com.mobilebadge.domain.Organization;
import com.mobilebadge.domain.Role;
import com.mobilebadge.domain.Store;
import com.mobilebadge.domain.UserAccount;
import com.mobilebadge.repo.ConsentPolicyRepository;
import com.mobilebadge.repo.OrganizationRepository;
import com.mobilebadge.repo.StoreRepository;
import com.mobilebadge.repo.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements ApplicationRunner {

  public static final String ADMIN_ACCOUNT = "10001";
  public static final String ADMIN_PASSWORD = "Admin123";
  public static final String SUPERVISOR_ACCOUNT = "10002";
  public static final String SUPERVISOR_PASSWORD = "Super123";
  public static final String ADVISOR_ACCOUNT = "10003";
  public static final String ADVISOR_PASSWORD = "Advisor123";

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final AppProperties properties;
  private final OrganizationRepository organizations;
  private final StoreRepository stores;
  private final UserAccountRepository users;
  private final ConsentPolicyRepository policies;
  private final PasswordEncoder passwordEncoder;

  public DataSeeder(
      AppProperties properties,
      OrganizationRepository organizations,
      StoreRepository stores,
      UserAccountRepository users,
      ConsentPolicyRepository policies,
      PasswordEncoder passwordEncoder) {
    this.properties = properties;
    this.organizations = organizations;
    this.stores = stores;
    this.users = users;
    this.policies = policies;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!properties.getSeed().isEnabled()) {
      return;
    }
    if (policies.count() == 0) {
      ConsentPolicy policy = new ConsentPolicy();
      policy.blockWithoutConsent = true;
      policy.validityDays = 30;
      policy.requirePerSession = false;
      policies.save(policy);
    }
    if (users.findByAccount(ADMIN_ACCOUNT).isPresent()) {
      return;
    }
    Organization org = new Organization();
    org.name = "体验中心";
    organizations.save(org);

    Store store = new Store();
    store.organizationId = org.id;
    store.name = "总店";
    store.enabled = true;
    stores.save(store);

    create(org.id, store.id, ADMIN_ACCOUNT, "系统管理员", ADMIN_PASSWORD, Role.ADMIN);
    create(org.id, store.id, SUPERVISOR_ACCOUNT, "门店主管", SUPERVISOR_PASSWORD, Role.SUPERVISOR);
    create(org.id, store.id, ADVISOR_ACCOUNT, "顾问甲", ADVISOR_PASSWORD, Role.ADVISOR);
    log.info("已写入默认组织与账号 {} / {} / {}", ADMIN_ACCOUNT, SUPERVISOR_ACCOUNT, ADVISOR_ACCOUNT);
  }

  private void create(Long orgId, Long storeId, String account, String name, String password, Role role) {
    UserAccount user = new UserAccount();
    user.organizationId = orgId;
    user.storeId = storeId;
    user.account = account;
    user.name = name;
    user.passwordHash = passwordEncoder.encode(password);
    user.role = role;
    user.enabled = true;
    users.save(user);
  }
}
