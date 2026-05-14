package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.auth.model.Role;
import group.moniepoint.eventsnestserver.auth.model.User;
import group.moniepoint.eventsnestserver.auth.model.VendorVerificationStatus;
import group.moniepoint.eventsnestserver.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSeeder {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    static final String DEFAULT_PASSWORD = "Password1!";

    static final String[][] USERS = {
            {"Akin",      "Ogundimu", "akin@devmail.com"},
            {"Chioma",    "Eze",      "chioma@devmail.com"},
            {"Emeka",     "Obi",      "emeka@devmail.com"},
            {"Funke",     "Adeyemi",  "funke@devmail.com"},
            {"Gbenga",    "Lawal",    "gbenga@devmail.com"},
            {"Halima",    "Musa",     "halima@devmail.com"},
            {"Ifeanyi",   "Nwosu",    "ifeanyi@devmail.com"},
            {"Jumoke",    "Bello",    "jumoke@devmail.com"},
            {"Kola",      "Adebayo",  "kola@devmail.com"},
            {"Lara",      "Williams", "lara@devmail.com"},
            {"Musa",      "Ibrahim",  "musa@devmail.com"},
            {"Ngozi",     "Okonkwo",  "ngozi@devmail.com"},
            {"Oluwatoyin","Adeyoke",  "oluwatoyin@devmail.com"},
            {"Precious",  "Nwankwo",  "precious@devmail.com"},
            {"Rasheed",   "Yusuf",    "rasheed@devmail.com"},
            {"Seun",      "Akanji",   "seun@devmail.com"},
            {"Tunde",     "Okafor",   "tunde@devmail.com"},
            {"Uche",      "Anyanwu",  "uche@devmail.com"},
            {"Vicky",     "Osei",     "vicky@devmail.com"},
            {"Wuraola",   "Lawal",    "wuraola@devmail.com"},
            {"Xolani",    "Zuma",     "xolani@devmail.com"},
            {"Yetunde",   "Ajayi",    "yetunde@devmail.com"},
            {"Zainab",    "Hassan",   "zainab@devmail.com"},
            {"Amara",     "Okafor",   "amara@devmail.com"},
            {"Bolaji",    "Oni",      "bolaji@devmail.com"},
            {"Chidinma",  "Obi",      "chidinma@devmail.com"},
            {"Daniel",    "Ibekwe",   "daniel@devmail.com"},
            {"Ebube",     "Eze",      "ebube@devmail.com"},
            {"Folake",    "Adekunle", "folake@devmail.com"},
            {"Gbemi",     "Kolade",   "gbemi@devmail.com"},
            {"Habiba",    "Musa",     "habiba@devmail.com"},
            {"Ibrahim",   "Ahmed",    "ibrahim@devmail.com"},
            {"Semi",      "Colon",    "semicolon@devmail.com"},
    };

    private record VerifiedVendorProfile(int userIndex, String serviceType, String bio) {}

    private static final List<VerifiedVendorProfile> VERIFIED_VENDORS = List.of(
            new VerifiedVendorProfile(3, "Catering",
                    "Full-service event caterer specialising in high-volume corporate and social events across Lagos."),
            new VerifiedVendorProfile(4, "Security",
                    "Licensed crowd management and VIP protection company with 50+ trained operatives."),
            new VerifiedVendorProfile(6, "MC / Host",
                    "Professional bilingual MC and stage host with 8 years of conference and gala experience."),
            new VerifiedVendorProfile(9, "Decoration",
                    "Award-winning event decorator — florals, bespoke installations, and branded environments."),
            new VerifiedVendorProfile(7, "First Aid / Medical",
                    "Certified paramedic team providing on-site medical cover for events of all sizes.")
    );

    List<User> seed() {
        Map<Integer, VerifiedVendorProfile> verifiedMap = new HashMap<>();
        for (VerifiedVendorProfile vvp : VERIFIED_VENDORS) {
            verifiedMap.put(vvp.userIndex(), vvp);
        }

        List<User> saved = new ArrayList<>();
        for (int i = 0; i < USERS.length; i++) {
            String[] d = USERS[i];
            VerifiedVendorProfile vvp = verifiedMap.get(i);

            User.UserBuilder builder = User.builder()
                    .firstName(d[0]).lastName(d[1]).email(d[2])
                    .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .role(Role.USER).enabled(true);

            if (vvp != null) {
                builder
                        .vendorVerified(true)
                        .vendorVerificationStatus(VendorVerificationStatus.VERIFIED)
                        .vendorServiceType(vvp.serviceType())
                        .vendorProfileDescription(vvp.bio())
                        .vendorVerificationSubmittedAt(LocalDateTime.now().minusDays(30))
                        .vendorVerifiedAt(LocalDateTime.now().minusDays(25));
            }

            saved.add(userRepository.save(builder.build()));
            log.debug("Seeded user: {} {} (vendor={})", d[0], d[1], vvp != null);
        }
        return saved;
    }

    List<User> reloadOrdered() {
        List<User> result = new ArrayList<>();
        for (String[] u : USERS) {
            userRepository.findByEmail(u[2]).ifPresent(result::add);
        }
        return result;
    }

    User seedSemicolon() {
        String[] d = USERS[32];
        return userRepository.findByEmail(d[2]).orElseGet(() -> {
            User u = userRepository.save(User.builder()
                    .firstName(d[0]).lastName(d[1]).email(d[2])
                    .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                    .role(Role.USER).enabled(true)
                    .build());
            log.info("Backfilled Semicolon user: {}", d[2]);
            return u;
        });
    }
}
