package group.moniepoint.eventsnestserver.seed;

import group.moniepoint.eventsnestserver.vendor.model.VendorApplication;
import group.moniepoint.eventsnestserver.vendor.model.VendorApplicationStatus;
import group.moniepoint.eventsnestserver.vendor.model.VendorRating;
import group.moniepoint.eventsnestserver.vendor.repository.VendorApplicationRepository;
import group.moniepoint.eventsnestserver.vendor.repository.VendorRatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSeeder {

    private final VendorApplicationRepository vendorApplicationRepository;
    private final VendorRatingRepository      vendorRatingRepository;

    private record ReviewSeed(int score, String comment) {}

    private static final Map<String, ReviewSeed> REVIEW_BY_SERVICE = Map.of(
            "Catering",           new ReviewSeed(5, "Exceptional catering — guests were very impressed. Prompt setup, great variety, and professional staff throughout the event."),
            "Security",           new ReviewSeed(4, "Reliable and professional. Crowd management was smooth and all access points were well-controlled. Would book again."),
            "MC / Host",          new ReviewSeed(5, "Outstanding MC — kept the energy high all day, handled transitions seamlessly and received great feedback from attendees."),
            "Decoration",         new ReviewSeed(4, "Beautiful venue transformation. Branding was on point and the floral arrangements exceeded our expectations."),
            "First Aid / Medical",new ReviewSeed(5, "Peace of mind having a qualified medical team on-site. Thankfully no incidents, but they were visibly prepared throughout.")
    );

    @Transactional
    void seed() {
        int count = 0;
        for (VendorApplication app : vendorApplicationRepository.findAll()) {
            if (app.getStatus() != VendorApplicationStatus.ACCEPTED) continue;
            if (vendorRatingRepository.findByVendorApplicationId(app.getId()).isPresent()) continue;

            if (app.getEvent().getEndTime() == null
                    || app.getEvent().getEndTime().isAfter(LocalDateTime.now())) {
                log.debug("Skipping rating for upcoming event: {}", app.getEvent().getTitle());
                continue;
            }

            ReviewSeed rs = REVIEW_BY_SERVICE.getOrDefault(app.getServiceType(),
                    new ReviewSeed(4, "Good service delivered as agreed. Satisfied with the overall performance."));

            vendorRatingRepository.save(VendorRating.builder()
                    .vendorApplication(app)
                    .ratedBy(app.getEvent().getCreatedBy())
                    .score(rs.score())
                    .comment(rs.comment())
                    .build());

            log.debug("Seeded vendor rating: {} → {} ({}★)", app.getServiceType(),
                    app.getApplicant().getFirstName(), rs.score());
            count++;
        }
        log.info("Seeded {} vendor ratings", count);
    }
}
