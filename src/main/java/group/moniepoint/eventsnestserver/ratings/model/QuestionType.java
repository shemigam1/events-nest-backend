package group.moniepoint.eventsnestserver.ratings.model;

public enum QuestionType {
    STAR,            // 1–5 star rating   → answerNumber
    NPS,             // 0–10 net promoter → answerNumber
    TEXT,            // free-text         → answerText
    MULTIPLE_CHOICE  // pick one option   → answerText (the chosen option)
}
